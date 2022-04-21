package us.kbase.workspace.kbase.admin;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.UObject;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;

/** A workspace administration mediator. Administration calls should be routed to this class,
 * which checks that the user is authorized, transforms the input parameters, and calls
 * the correct method on the correct object.
 * 
 * The mediator caches each administrators's {@link AdminRole} for a specified amount of time to
 * avoid excessive calls to the {@link AdministratorHandler}, which may make network calls.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceAdministration {
	
	private static final String REMOVE_ADMIN = "removeAdmin";
	private static final String ADD_ADMIN = "addAdmin";
	private static final String LIST_ADMINS = "listAdmins";
	
	/** Commands that are reserved for use by {@link WorkspaceAdministration}. Attempting
	 * to install a command with one of these names is an error.
	 */
	public static final Set<String> RESERVED_COMMANDS = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(REMOVE_ADMIN, ADD_ADMIN, LIST_ADMINS)));
	
	/** The default maximum size of the admin user cache. */
	public static final int DEFAULT_CACHE_MAX_SIZE = 100; // seems like more than enough admins
	
	/** The default amount time an administrator will be cached in milliseconds. */
	public static final int DEFAULT_CACHE_EXP_TIME_MS = 5 * 60 * 1000; // cache admin role for 5m
	
	/** An interface for handling an administration command. */
	@FunctionalInterface
	public static interface AdminCommandHandler {
		
		/** Run the command.
		 * @param cmd the command.
		 * @param token the user's token.
		 * @param resourcesToDelete a threadlocal in which resources that must be deleted when
		 * the command is completed can be stored.
		 * @return the result of the command.
		 * @throws Exception if an error occurs.
		 */
		Object runCommand(
				AdminCommand command,
				AuthToken token,
				// there's got to be a better way to deal with this...
				ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete)
			throws Exception;
	}
	
	/** A specification for an administration command. */
	public static class AdminCommandSpecification {
		private final String commandName;
		private final AdminCommandHandler commandHandler;
		private final boolean requireWrite;
		
		/** Create the specification.
		 * @param the name of the command to respond to.
		 * @param commandHandler the handler for the command.
		 */
		public AdminCommandSpecification(
				final String commandName,
				final AdminCommandHandler commandHandler) {
			this(commandName, commandHandler, false);
		}
		
		/** Create the specification.
		 * @param the name of the command to respond to.
		 * @param commandHandler the handler for the command.
		 * @param requireWrite true if this command requires write privileges.
		 */
		public AdminCommandSpecification(
				final String commandName,
				final AdminCommandHandler commandHandler,
				final boolean requireWrite) {
			this.commandName = requireNonNull(commandName, "commandName");
			this.commandHandler = requireNonNull(commandHandler, "commandHandler");
			this.requireWrite = requireWrite;
		}
		
		/** Get the name of the command.
		 * @return the name.
		 */
		public String getName() {
			return this.commandName;
		}

		/** Run the command.
		 * @param cmd the command.
		 * @param token the user's token.
		 * @param resourcesToDelete a threadlocal in which resources that must be deleted when
		 * the command is completed can be stored.
		 * @return the result of the command.
		 * @throws Exception if an error occurs.
		 */
		public Object runCommand(
				final AdminCommand cmd,
				final AuthToken token,
				final ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete)
				throws Exception {
			return commandHandler.runCommand(cmd, token, resourcesToDelete);
		}

		/** Whether write privileges are required to run this command.
		 * @return true if write privileges are required.
		 */
		public boolean isRequireWrite() {
			return requireWrite;
		}
	}
	
	/** A validator for KBase users. */
	@FunctionalInterface
	public static interface UserValidator {
		
		/** Validate and get a user.
		 * @param user the KBase user name.
		 * @param token a valid KBase token, which need not be for the user to be validated.
		 * @return the user name.
		 * @throws IOException if an IOException occurs.
		 * @throws AuthException if an error occurs validating the user.
		 */
		WorkspaceUser validateUser(String user, AuthToken token) throws IOException, AuthException;
	}
	
	private final AdministratorHandler admin;
	private final Cache<String, AdminRole> adminCache;
	private final Map<String, AdminCommandSpecification> commandHandlers;
	private final UserValidator userValidator;
	
	private WorkspaceAdministration(
			final AdministratorHandler admin,
			final Map<String, AdminCommandSpecification> commandHandlers,
			final UserValidator userValidator,
			final int maxCacheSize,
			final int cacheTimeInMS,
			final Ticker ticker) {
		this.admin = admin;
		adminCache = CacheBuilder.newBuilder()
				.maximumSize(maxCacheSize)
				.expireAfterWrite(cacheTimeInMS, TimeUnit.MILLISECONDS)
				.ticker(ticker)
				.build();
		this.commandHandlers = commandHandlers;
		this.userValidator = userValidator;
		installAdminCommandSpecs();
	}
	
	private void installAdminCommandSpecs() {
		final List<AdminCommandSpecification> specs = Arrays.asList(
			new AdminCommandSpecification(
				LIST_ADMINS,
				(cmd, tok, toDelete) -> {
					getLogger().info(LIST_ADMINS);
					return admin.getAdmins().stream().map(u -> u.getUser())
							.collect(Collectors.toList());
				}),
			new AdminCommandSpecification(
				ADD_ADMIN,
				(cmd, tok, toDelete) -> {
					final WorkspaceUser user = userValidator.validateUser(cmd.getUser(), tok);
					getLogger().info(ADD_ADMIN + " " + user.getUser());
					admin.addAdmin(user);
					adminCache.invalidate(user.getUser());
					return null;
				},
				true),
			new AdminCommandSpecification(
				REMOVE_ADMIN,
				(cmd, tok, toDelete) -> {
					final WorkspaceUser user = userValidator.validateUser(cmd.getUser(), tok);
					getLogger().info(REMOVE_ADMIN + " " + user.getUser());
					admin.removeAdmin(user);
					adminCache.invalidate(user.getUser());
					return null;
				},
				true)
			);
		
		for (final AdminCommandSpecification spec: specs) {
			commandHandlers.put(spec.getName(), spec);
		}
	}
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(WorkspaceAdministration.class);
	}
	
	private void checkRequireWrite(
			final AdminCommandSpecification cmdspec,
			final AdminRole role) {
		if (cmdspec.isRequireWrite() && !AdminRole.ADMIN.equals(role)) {
			throw new IllegalArgumentException(
					"Full administration rights required for this command");
		}
	}
	
	private AdminRole getAdminRole(final AuthToken token) throws AdministratorHandlerException {
		try {
			return adminCache.get(token.getUserName(), new Callable<AdminRole>() {
				
				@Override
				public AdminRole call() throws AdministratorHandlerException {
					return admin.getAdminRole(token);
				}
			});
		} catch (ExecutionException e) {
			throw (AdministratorHandlerException) e.getCause();
		}
	}

	/** Run an administration command.
	 * @param token the administrator's token.
	 * @param command the command to run. This is expected to contain an {@link AdminCommand}
	 * class instance.
	 * @param resourcesToDelete a container for deleted once the command is complete.
	 * @return the result of the command.
	 * @throws Exception if any exception occurs.
	 */
	public Object runCommand(
			final AuthToken token,
			final UObject command,
			final ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete)
			throws Exception {
		final AdminRole role = getAdminRole(token);
		if (AdminRole.NONE.equals(role)) {
			throw new IllegalArgumentException("User " + token.getUserName() + " is not an admin");
		}
		final AdminCommand cmd;
		try {
			cmd = command.asClassInstance(AdminCommand.class);
		} catch (IllegalStateException ise) {
			final IOException ioe = (IOException) ise.getCause();
			if (ioe instanceof JsonMappingException || ioe instanceof JsonParseException) {
				throw new IllegalArgumentException("Unable to deserialize " +
						"a workspace admin command from the input: " + ioe.getMessage(), ioe);
			}
			throw ioe;
		}
		final String fn = cmd.getCommand();
		if (!commandHandlers.containsKey(fn)) {
			throw new IllegalArgumentException(
					"I don't know how to process the command: " + fn);
		}
		checkRequireWrite(commandHandlers.get(fn), role);
		return commandHandlers.get(fn).runCommand(cmd, token, resourcesToDelete);
	}
	
	/** Get a builder for a {@link WorkspaceAdministration}.
	 * @param admin an administrator handler instance.
	 * @param userValidator a user validator instance.
	 * @return the builder.
	 */
	public static final Builder getBuilder(
			final AdministratorHandler admin,
			final UserValidator userValidator) {
		return new Builder(admin, userValidator);
	}
	
	// TODO NOW unit tests for this class. PR is getting too big
	
	/** A builder for a {@link WorkspaceAdministration}. */
	public static class Builder {
		
		private final AdministratorHandler admin;
		private final UserValidator userValidator;
		private final Map<String, AdminCommandSpecification> commandHandlers = new HashMap<>();
		private int maxCacheSize = DEFAULT_CACHE_MAX_SIZE;
		private int cacheTimeInMS = DEFAULT_CACHE_EXP_TIME_MS;
		private Ticker ticker = Ticker.systemTicker();
		
		private Builder(final AdministratorHandler admin, final UserValidator userValidator) {
			this.admin = requireNonNull(admin, "admin");
			this.userValidator = requireNonNull(userValidator, "userValidator");
		}
		
		/** Set the maximum number of users to be kept in the administrator cache. The cache
		 * prevents multiple rapid lookups of the admin status of users, which are
		 * likely to be over network connections and therefore expensive.
		 * @param maxCacheSize the maximum number of users, defaulting to
		 * {@link WorkspaceAdministration#DEFAULT_CACHE_MAX_SIZE}.
		 * @return this builder.
		 */
		public Builder withCacheMaxSize(final int maxCacheSize) {
			if (maxCacheSize < 0) {
				throw new IllegalArgumentException("maxCacheSize must be >= 0");
			}
			this.maxCacheSize = maxCacheSize;
			return this;
		}
		
		/** Set the amount of time users remain in the administrator cache. Any change in status
		 * at the source of the administrator information will not be reflected until the user
		 * expires from the cache.
		 * @param cacheTimeMS the cache time in milliseconds, defaulting to
		 * {@link WorkspaceAdministration#DEFAULT_CACHE_MAX_SIZE}.
		 * @return this builder.
		 */
		public Builder withCacheTimeMS(final int cacheTimeMS) {
			if (cacheTimeMS < 0) {
				throw new IllegalArgumentException("cacheTimeMS must be >= 0");
			}
			this.cacheTimeInMS = cacheTimeMS;
			return this;
		}
		
		/** Set the ticker for the administrator; generally only useful for testing purposes.
		 * @param ticker the ticker. Null input is silently ignored.
		 * @return this builder.
		 */
		public Builder withCacheTicker(final Ticker ticker) {
			if (ticker != null) {
				this.ticker = ticker;
			}
			return this;
		}
		
		/** Add an admin command to the administration instance. Cannot be a reserved command in
		 * {@link WorkspaceAdministration#RESERVED_COMMANDS}.
		 * @param spec the admin command.
		 * @return this builder.
		 */
		public Builder withCommand(final AdminCommandSpecification spec) {
			if (RESERVED_COMMANDS.contains(requireNonNull(spec, "spec").getName())) {
				throw new IllegalArgumentException("Reserved command: " + spec.getName());
			}
			commandHandlers.put(spec.getName(), spec);
			return this;
		}
		
		/** Build the {@link WorkspaceAdministration}.
		 * @return the administration instance.
		 */
		public WorkspaceAdministration build() {
			return new WorkspaceAdministration(
					admin, commandHandlers, userValidator, maxCacheSize, cacheTimeInMS, ticker);
		}
	}
}
