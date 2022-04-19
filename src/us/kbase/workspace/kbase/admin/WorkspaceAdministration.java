package us.kbase.workspace.kbase.admin;

import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.IdentifierUtils.processWorkspaceIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JacksonTupleModule;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.GetPermissionsMassParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceIDsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.SetWorkspaceDescriptionParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.WorkspaceServerMethods;

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
	
	private static final String GET_CONFIG = "getConfig";
	private static final String SET_CONFIG = "setConfig";
	
	private static final String DENY_MOD_REQUEST = "denyModRequest";
	private static final String APPROVE_MOD_REQUEST = "approveModRequest";
	private static final String LIST_MOD_REQUESTS = "listModRequests";

	private static final String REMOVE_ADMIN = "removeAdmin";
	private static final String ADD_ADMIN = "addAdmin";
	private static final String LIST_ADMINS = "listAdmins";

	private static final String SET_WORKSPACE_OWNER = "setWorkspaceOwner";
	private static final String LIST_WORKSPACE_OWNERS = "listWorkspaceOwners";

	private static final String REMOVE_MODULE_OWNERSHIP = "removeModuleOwnership";
	private static final String GRANT_MODULE_OWNERSHIP = "grantModuleOwnership";
	private static final String LIST_WORKSPACES = "listWorkspaces";
	private static final String LIST_WORKSPACE_IDS = "listWorkspaceIDs";
	private static final String GET_WORKSPACE_DESCRIPTION = "getWorkspaceDescription";
	private static final String SET_WORKSPACE_DESCRIPTION = "setWorkspaceDescription";
	private static final String LIST_OBJECTS = "listObjects";
	private static final String SAVE_OBJECTS = "saveObjects";
	private static final String GET_OBJECT_INFO = "getObjectInfo";
	private static final String GET_OBJECT_HIST = "getObjectHistory";
	private static final String GET_OBJECTS = "getObjects";
	private static final String SET_GLOBAL_PERMISSION = "setGlobalPermission";
	private static final String GET_WORKSPACE_INFO = "getWorkspaceInfo";
	private static final String GET_PERMISSIONS = "getPermissions";
	private static final String GET_PERMISSIONS_MASS = "getPermissionsMass";
	private static final String SET_PERMISSIONS = "setPermissions";
	private static final String CREATE_WORKSPACE = "createWorkspace";
	private static final String DELETE_WS = "deleteWorkspace";
	private static final String UNDELETE_WS = "undeleteWorkspace";

	private final static ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JacksonTupleModule());
	
	private final WorkspaceServerMethods wsmeth;
	private final Types types;
	private final AdministratorHandler admin;
	private final Cache<String, AdminRole> adminCache;
	
	/** Create the workspace administration instance.
	 * @param ws a workspace instance.
	 * @param wsmeth a workspace methods instance.
	 * @param types a workspace types instance.
	 * @param admin an administrator handler.
	 * @param maxCacheSize the maximum number of {@link AdminRole}s to cache.
	 * @param cacheTimeInMS the maximum time an {@link AdminRole} will be cached in milliseconds.
	 */
	public WorkspaceAdministration(
			final WorkspaceServerMethods wsmeth,
			final Types types,
			final AdministratorHandler admin,
			final int maxCacheSize,
			final int cacheTimeInMS) {
		this(wsmeth, types, admin, maxCacheSize, cacheTimeInMS, Ticker.systemTicker());
	}
	
	/** This constructor should only be used for tests. */
	public WorkspaceAdministration(
			final WorkspaceServerMethods wsmeth,
			final Types types,
			final AdministratorHandler admin,
			final int maxCacheSize,
			final int cacheTimeInMS,
			final Ticker ticker) {
		this.types = types;
		// TODO CODE some of the code here calls the underlying workspace instance.
		// probably better to not do that and just call the service translation layer when
		// possible.
		this.wsmeth = wsmeth;
		this.admin = admin;
		adminCache = CacheBuilder.newBuilder()
				.maximumSize(maxCacheSize)
				.expireAfterWrite(cacheTimeInMS, TimeUnit.MILLISECONDS)
				.ticker(ticker)
				.build();
	}
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(WorkspaceAdministration.class);
	}
	
	private void requireWrite(final AdminRole role) {
		if (!AdminRole.ADMIN.equals(role)) {
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
		final String putativeAdmin = token.getUserName();
		if (AdminRole.NONE.equals(role)) {
			throw new IllegalArgumentException("User " + putativeAdmin + " is not an admin");
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
		// should look into some sort of interface w/ registration instead of a massive if else
		final String fn = cmd.getCommand();
		if (GET_CONFIG.contentEquals(fn)) {
			getLogger().info(GET_CONFIG);
			return ImmutableMap.of("config", wsmeth.getWorkspace().getConfig().toMap());
		}
		if (SET_CONFIG.equals(fn)) {
			requireWrite(role);
			getLogger().info(SET_CONFIG); // add parameters?
			final SetConfigParams params = getParams(cmd, SetConfigParams.class);
			wsmeth.getWorkspace().setConfig(DynamicConfigUpdate.getBuilder().withMap(params.set)
					.build());
			return null;
		}
		if (LIST_MOD_REQUESTS.equals(fn)) {
			getLogger().info(LIST_MOD_REQUESTS);
			return types.listModuleRegistrationRequests();
		}
		if (APPROVE_MOD_REQUEST.equals(fn)) {
			requireWrite(role);
			final String mod = cmd.getModule();
			getLogger().info(APPROVE_MOD_REQUEST + " " + mod);
			types.resolveModuleRegistration(mod, true);
			return null;
		}
		if (DENY_MOD_REQUEST.equals(fn)) {
			requireWrite(role);
			final String mod = cmd.getModule();
			getLogger().info(DENY_MOD_REQUEST + " " + mod);
			types.resolveModuleRegistration(mod, false);
			return null;
		}
		if (LIST_ADMINS.equals(fn)) {
			getLogger().info(LIST_ADMINS);
			return usersToStrings(admin.getAdmins());
		}
		if (ADD_ADMIN.equals(fn)) {
			requireWrite(role);
			final WorkspaceUser user = getUser(cmd, token);
			getLogger().info(ADD_ADMIN + " " + user.getUser());
			admin.addAdmin(user);
			adminCache.invalidate(user.getUser());
			return null;
		}
		if (REMOVE_ADMIN.equals(fn)) {
			requireWrite(role);
			final WorkspaceUser user = getUser(cmd, token);
			getLogger().info(REMOVE_ADMIN + " " + user.getUser());
			admin.removeAdmin(user);
			adminCache.invalidate(user.getUser());
			return null;
		}
		if (SET_WORKSPACE_OWNER.equals(fn)) {
			requireWrite(role);
			final SetWorkspaceOwnerParams params = getParams(cmd, SetWorkspaceOwnerParams.class);
			
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(params.wsi);
			final WorkspaceInformation info = wsmeth.getWorkspace().setWorkspaceOwner(
					null,
					wsi,
					params.new_user == null ? null : getUser(params.new_user, token),
					Optional.ofNullable(params.new_name), true);
			getLogger().info(SET_WORKSPACE_OWNER + " " + info.getId() + " " +
					info.getOwner().getUser());
			return wsInfoToTuple(info);
		}
		if (CREATE_WORKSPACE.equals(fn)) {
			requireWrite(role);
			final WorkspaceUser user = getUser(cmd, token);
			final CreateWorkspaceParams params = getParams(cmd, CreateWorkspaceParams.class);
			final Tuple9<Long, String, String, String, Long, String, String, String,
					Map<String, String>> ws =  wsmeth.createWorkspace(params, user);
			getLogger().info(CREATE_WORKSPACE + " " + ws.getE1() + " " + ws.getE3());
			return ws;
		}
		if (SET_PERMISSIONS.equals(fn)) {
			requireWrite(role);
			final SetPermissionsParams params = getParams(cmd, SetPermissionsParams.class);
			final long id = wsmeth.setPermissionsAsAdmin(params, token);
			getLogger().info(SET_PERMISSIONS + " " + id + " " + params.getNewPermission() + " " +
					StringUtils.join(params.getUsers(), " "));
			return null;
		}
		if (SET_WORKSPACE_DESCRIPTION.equals(fn)) {
			requireWrite(role);
			final SetWorkspaceDescriptionParams params = getParams(
					cmd, SetWorkspaceDescriptionParams.class);
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
					params.getWorkspace(), params.getId());
			final long id = wsmeth.getWorkspace().setWorkspaceDescription(
					null, wsi, params.getDescription(), true);
			getLogger().info(SET_WORKSPACE_DESCRIPTION + " " + id);
			return null;
		}
		if (GET_WORKSPACE_DESCRIPTION.equals(fn)) {
			final WorkspaceIdentity wsi = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wsid = processWorkspaceIdentifier(wsi);
			final String desc = wsmeth.getWorkspace().getWorkspaceDescription(null, wsid, true);
			// TODO FEATURE would be better if could always provide ID vs. name
			getLogger().info(GET_WORKSPACE_DESCRIPTION + " " + wsi.getId() + " " +
					wsi.getWorkspace());
			return desc;
		}
		if (GET_PERMISSIONS.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceUser user = getNullableUser(cmd, token);
			final Map<String, String> perms;
			if (user == null) {
				perms = wsmeth.getPermissions(Arrays.asList(params), null, true).getPerms().get(0);
			} else {
				perms = wsmeth.getPermissions(params, user);
			}
			//TODO FEATURE would be better if could always provide ID vs. name
			getLogger().info(GET_PERMISSIONS + " " + params.getId() + " " +
					params.getWorkspace() + (user == null ? "" : " " + user.getUser()));
			return perms;
		}
		if (GET_PERMISSIONS_MASS.equals(fn)) {
			final GetPermissionsMassParams params = getParams(cmd, GetPermissionsMassParams.class);
			// not sure what to log here, could be 1K entries.
			final WorkspacePermissions perms = wsmeth.getPermissions(
					params.getWorkspaces(), null, true);
			getLogger().info(GET_PERMISSIONS_MASS + " " + params.getWorkspaces().size() +
					" workspaces in input");
			return perms;
		}
		if (GET_WORKSPACE_INFO.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
							params.getWorkspace(), params.getId());
			final WorkspaceInformation info = wsmeth.getWorkspace().
					getWorkspaceInformationAsAdmin(wsi);
			getLogger().info(GET_WORKSPACE_INFO + " " + info.getId());
			return wsInfoToTuple(info);
		}
		if (SET_GLOBAL_PERMISSION.equals(fn)) {
			requireWrite(role);
			final WorkspaceUser user = getUser(cmd, token);
			final SetGlobalPermissionsParams params = getParams(cmd,
					SetGlobalPermissionsParams.class);
			final long id = wsmeth.setGlobalPermission(params, user);
			getLogger().info(SET_GLOBAL_PERMISSION + " " + id + " " +
					params.getNewPermission() + " " + user.getUser());
			return null;
		}
		if (SAVE_OBJECTS.equals(fn)) {
			requireWrite(role);
			final WorkspaceUser user = getUser(cmd, token);
			final SaveObjectsParams params = getParams(cmd, SaveObjectsParams.class);
			//method has its own logging
			getLogger().info(SAVE_OBJECTS + " " + user.getUser());
			return wsmeth.saveObjects(params, user, token);
		}
		if (GET_OBJECT_INFO.equals(fn)) {
			final GetObjectInfo3Params params = getParams(cmd, GetObjectInfo3Params.class);
			// method has its own logging
			getLogger().info(GET_OBJECT_INFO);
			// is passing the admin user really necessary? Check at some point, maybe remove
			return wsmeth.getObjectInformation(params, new WorkspaceUser(putativeAdmin), true);
		}
		if (GET_OBJECT_HIST.equals(fn)) {
			final ObjectIdentity params = getParams(cmd, ObjectIdentity.class);
			// method has its own logging
			getLogger().info(GET_OBJECT_HIST);
			return wsmeth.getObjectHistory(params, new WorkspaceUser(putativeAdmin), true);
		}
		if (GET_OBJECTS.equals(fn)) {
			final GetObjects2Params params = getParams(cmd, GetObjects2Params.class);
			// method has its own logging
			getLogger().info(GET_OBJECTS);
			return wsmeth.getObjects(params, new WorkspaceUser(putativeAdmin), true,
					resourcesToDelete);
		}
		if (LIST_WORKSPACES.equals(fn)) {
			final WorkspaceUser user = getUser(cmd, token);
			final ListWorkspaceInfoParams params = getParams(cmd, ListWorkspaceInfoParams.class);
			getLogger().info(LIST_WORKSPACES + " " + user.getUser());
			return wsmeth.listWorkspaceInfo(params, user);
		}
		if (LIST_WORKSPACE_IDS.equals(fn)) {
			final WorkspaceUser user = getUser(cmd, token);
			final ListWorkspaceIDsParams params = getParams(cmd, ListWorkspaceIDsParams.class);
			getLogger().info(LIST_WORKSPACE_IDS + " " + user.getUser());
			return wsmeth.listWorkspaceIDs(params, user);
		}
		if (LIST_OBJECTS.equals(fn)) {
			final ListObjectsParams params = getParams(cmd, ListObjectsParams.class);
			final WorkspaceUser user = getNullableUser(cmd, token);
			final String ustr = user == null ? "adminuser" : "user: " + user.getUser();
			getLogger().info(LIST_OBJECTS + " " + ustr);
			return wsmeth.listObjects(params, user, user == null);
		}
		if (DELETE_WS.equals(fn)) {
			requireWrite(role);
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
			final long id = wsmeth.getWorkspace().setWorkspaceDeleted(null, wksp, true, true);
			getLogger().info(DELETE_WS + " " + id);
			return null;
		}
		if (UNDELETE_WS.equals(fn)) {
			requireWrite(role);
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
			final long id = wsmeth.getWorkspace().setWorkspaceDeleted(null, wksp, false, true);
			getLogger().info(UNDELETE_WS + " " + id);
			return null;
		}
		if (LIST_WORKSPACE_OWNERS.equals(fn)) {
			getLogger().info(LIST_WORKSPACE_OWNERS);
			return usersToStrings(wsmeth.getWorkspace().getAllWorkspaceOwners());
		}
		if (GRANT_MODULE_OWNERSHIP.equals(fn)) {
			requireWrite(role);
			final GrantModuleOwnershipParams params = getParams(cmd,
					GrantModuleOwnershipParams.class);
			getLogger().info(GRANT_MODULE_OWNERSHIP + " " + params.getMod() +
					" " + params.getNewOwner());
			wsmeth.grantModuleOwnership(params, null, true);
			return null;
		}
		if (REMOVE_MODULE_OWNERSHIP.equals(fn)) {
			requireWrite(role);
			final RemoveModuleOwnershipParams params = getParams(cmd,
					RemoveModuleOwnershipParams.class);
			getLogger().info(REMOVE_MODULE_OWNERSHIP + " " + params.getMod() +
					" " + params.getOldOwner());
			wsmeth.removeModuleOwnership(params, null, true);
			return null;
		}
		throw new IllegalArgumentException(
				"I don't know how to process the command: " + fn);
	}

	private List<String> usersToStrings(final Set<WorkspaceUser> users) {
		final List<String> ret = new ArrayList<String>();
		for (final WorkspaceUser u: users) {
			ret.add(u.getUser());
		}
		return ret;
	}

	private WorkspaceUser getUser(final AdminCommand cmd, final AuthToken token)
			throws IOException, AuthException {
		return getUser(cmd.getUser(), token);
	}

	private WorkspaceUser getUser(final String user, final AuthToken token)
			throws IOException, AuthException {
		if (user == null) {
			throw new NullPointerException("User may not be null");
		}
		return wsmeth.validateUsers(Arrays.asList(user), token).get(0);
	}
	
	private WorkspaceUser getNullableUser(final AdminCommand cmd, final AuthToken token)
			throws IOException, AuthException {
		if (cmd.getUser() == null) {
			return null;
		}
		return wsmeth.validateUsers(Arrays.asList(cmd.getUser()), token).get(0);
	}
	
	private static class SetConfigParams {
		public Map<String, Object> set;
		
		@SuppressWarnings("unused")
		public SetConfigParams() {}; // for jackson
	}
	
	private static class SetWorkspaceOwnerParams {
		public WorkspaceIdentity wsi;
		public String new_user;
		public String new_name;
		
		@SuppressWarnings("unused")
		public SetWorkspaceOwnerParams() {}; //for jackson
	}
	
	private <T> T getParams(final AdminCommand input, final Class<T> clazz)
			throws IOException {
		final UObject p = input.getParams();
		if (p == null) {
			throw new NullPointerException("Method parameters " +
					clazz.getSimpleName() + " may not be null");
		}
		try {
			return MAPPER.readValue(p.getPlacedStream(), clazz);
		} catch (JsonMappingException e) { // parse exception can't happen here
			throw new IllegalArgumentException("Unable to deserialize "
					+ clazz.getSimpleName() + " out of params field: " + e.getMessage(), e);
		}
	}

//	//why doesn't this work?
//	@SuppressWarnings("unused")
//	private <T> T getParams(final Map<String, Object> input) {
//		return UObject.transformObjectToObject(input.get("params"),
//				new TypeReference<T>() {});
//	}
}
