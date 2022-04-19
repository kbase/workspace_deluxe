package us.kbase.workspace.kbase.admin;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
	
	/** Create the workspace administration instance.
	 * @param admin an administrator handler.
	 * @param maxCacheSize the maximum number of {@link AdminRole}s to cache.
	 * @param cacheTimeInMS the maximum time an {@link AdminRole} will be cached in milliseconds.
	 */
	public WorkspaceAdministration(
			final AdministratorHandler admin,
			// TODO NOW this is temporary to reduce PR size. Change to a builder & add unit tests
			// Also redo javadoc after that
			final Map<String, AdminCommandSpecification> commandHandlers,
			final UserValidator userValidator,
			final int maxCacheSize,
			final int cacheTimeInMS) {
		this(admin, commandHandlers, userValidator, maxCacheSize,
				cacheTimeInMS, Ticker.systemTicker());
	}
	
	/** This constructor should only be used for tests. */
	public WorkspaceAdministration(
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
}
