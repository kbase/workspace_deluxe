package us.kbase.workspace.kbase;

import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.IdentifierUtils.processWorkspaceIdentifier;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JacksonTupleModule;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetPermissionsMassParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class WorkspaceAdministration {
	
	//TODO JAVADOC
	
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
	private static final String LIST_OBJECTS = "listObjects";
	private static final String SAVE_OBJECTS = "saveObjects";
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
	
	private final Workspace ws;
	private final WorkspaceServerMethods wsmeth;
	private final Types types;
	private final Set<String> internaladmins = new HashSet<String>(); 
	
	public WorkspaceAdministration(
			final Workspace ws, 
			final WorkspaceServerMethods wsmeth,
			final Types types,
			final String admin) {
		this.ws = ws;
		this.types = types;
		this.wsmeth = wsmeth;
		if (admin != null && !admin.isEmpty()) {
			internaladmins.add(admin);
		}
	}
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(WorkspaceAdministration.class);
	}

	public Object runCommand(AuthToken token, UObject command)
			throws TypeStorageException, IOException, AuthException,
			WorkspaceCommunicationException, PreExistingWorkspaceException,
			CorruptWorkspaceDBException, NoSuchObjectException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			ParseException, NoSuchPrivilegeException,
			TypedObjectValidationException, TypedObjectSchemaException,
			MetadataException {
		final String putativeAdmin = token.getUserName();
		if (!(internaladmins.contains(putativeAdmin) ||
				ws.isAdmin(new WorkspaceUser(putativeAdmin)))) {
			throw new IllegalArgumentException("User " + putativeAdmin + " is not an admin");
		}
		final AdminCommand cmd;
		try {
			cmd = command.asClassInstance(AdminCommand.class);
		} catch (IllegalStateException ise) {
			final IOException ioe = (IOException) ise.getCause();
			if (ioe instanceof JsonMappingException) {
				throw new IllegalArgumentException("Unable to deserialize " +
						"a workspace admin command from the input.", ioe);
			}
			throw ioe;
		}
		// should look into some sort of interface w/ registration instead of a massive if else
		final String fn = (String) cmd.getCommand();
		if (LIST_MOD_REQUESTS.equals(fn)) {
			getLogger().info(LIST_MOD_REQUESTS);
			return types.listModuleRegistrationRequests();
		}
		if (APPROVE_MOD_REQUEST.equals(fn)) {
			final String mod = cmd.getModule();
			getLogger().info(APPROVE_MOD_REQUEST + " " + mod);
			types.resolveModuleRegistration(mod, true);
			return null;
		}
		if (DENY_MOD_REQUEST.equals(fn)) {
			final String mod = cmd.getModule();
			getLogger().info(DENY_MOD_REQUEST + " " + mod);
			types.resolveModuleRegistration(mod, false);
			return null;
		}
		if (LIST_ADMINS.equals(fn)) {
			getLogger().info(LIST_ADMINS);
			final Set<String> strAdm = new HashSet<String>();
			strAdm.addAll(usersToStrings(ws.getAdmins()));
			strAdm.addAll(internaladmins);
			return strAdm;
		}
		if (ADD_ADMIN.equals(fn)) {
			final WorkspaceUser user = getUser(cmd);
			getLogger().info(ADD_ADMIN + " " + user.getUser());
			ws.addAdmin(user);
			return null;
		}
		if (REMOVE_ADMIN.equals(fn)) {
			final WorkspaceUser user = getUser(cmd);
			getLogger().info(REMOVE_ADMIN + " " + user.getUser());
			ws.removeAdmin(user);
			return null;
		}
		if (SET_WORKSPACE_OWNER.equals(fn)) {
			final SetWorkspaceOwnerParams params =
					getParams(cmd, SetWorkspaceOwnerParams.class);
			
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
							params.wsi);
			final WorkspaceInformation info = ws.setWorkspaceOwner(null, wsi,
					params.new_user == null ? null :
					getUser(params.new_user), Optional.fromNullable(params.new_name), true);
			getLogger().info(SET_WORKSPACE_OWNER + " " + info.getId() + " " +
					info.getOwner().getUser());
			return wsInfoToTuple(info);
		}
		if (CREATE_WORKSPACE.equals(fn)) {
			final CreateWorkspaceParams params = getParams(cmd,
					CreateWorkspaceParams.class);
			Tuple9<Long, String, String, String, Long, String, String, String,
			Map<String, String>> ws =  wsmeth.createWorkspace(
					params, getUser(cmd));
			getLogger().info(CREATE_WORKSPACE + " " + ws.getE1() + " " +
					ws.getE3());
			return ws;
		}
		if (SET_PERMISSIONS.equals(fn)) {
			final SetPermissionsParams params = getParams(cmd,
					SetPermissionsParams.class);
			//TODO FEATURE maybe set perms should return wsinfo so can provide ID vs. name
			getLogger().info(SET_PERMISSIONS + " " + params.getId() + " " +
					params.getWorkspace() + " " + params.getNewPermission() +
					" " + StringUtils.join(params.getUsers(), " "));
			wsmeth.setPermissions(params, null, true);
			return null;
		}
		if (GET_PERMISSIONS.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceUser user = getNullableUser(cmd);
			//TODO FEATURE would be better if could always provide ID vs. name
			getLogger().info(GET_PERMISSIONS + " " + params.getId() + " " +
					params.getWorkspace() + (user == null ? "" : " " + user.getUser()));
			if (user == null) {
				return wsmeth.getPermissions(Arrays.asList(params), null, true).getPerms().get(0);
			} else {
				return wsmeth.getPermissions(params, user);
			}
		}
		if (GET_PERMISSIONS_MASS.equals(fn)) {
			final GetPermissionsMassParams params = getParams(cmd, GetPermissionsMassParams.class);
			// not sure what to log here, could be 1K entries.
			getLogger().info(GET_PERMISSIONS_MASS + " " + params.getWorkspaces().size() +
					" workspaces in input");
			return wsmeth.getPermissions(params.getWorkspaces(), null, true);
		}
		if (GET_WORKSPACE_INFO.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
							params.getWorkspace(), params.getId());
			final WorkspaceInformation info = ws.getWorkspaceInformationAsAdmin(wsi);
			getLogger().info(GET_WORKSPACE_INFO + " " + info.getId());
			return wsInfoToTuple(info);
		}
		if (SET_GLOBAL_PERMISSION.equals(fn)) {
			final SetGlobalPermissionsParams params = getParams(cmd,
					SetGlobalPermissionsParams.class);
			final WorkspaceUser user = getUser(cmd);
			//TODO FEATURE would be better if could provide ID vs. name
			getLogger().info(SET_GLOBAL_PERMISSION + " " + params.getId() +
					" " + params.getWorkspace() + " " +
					params.getNewPermission() + " " + user.getUser());
			wsmeth.setGlobalPermission(params, getUser(cmd));
			return null;
		}
		if (SAVE_OBJECTS.equals(fn)) {
			final SaveObjectsParams params = getParams(cmd,
					SaveObjectsParams.class);
			final WorkspaceUser user = getUser(cmd);
			//method has its own logging
			getLogger().info(SAVE_OBJECTS + " " + user.getUser());
			return wsmeth.saveObjects(params, user, token);
		}
		if (LIST_WORKSPACES.equals(fn)) {
			final ListWorkspaceInfoParams params = getParams(cmd, ListWorkspaceInfoParams.class);
			final WorkspaceUser user = getUser(cmd);
			getLogger().info(LIST_WORKSPACES + " " + user.getUser());
			return wsmeth.listWorkspaceInfo(params, user);
		}
		if (LIST_OBJECTS.equals(fn)) {
			final ListObjectsParams params = getParams(cmd, ListObjectsParams.class);
			final WorkspaceUser user = getUser(cmd); // TODO NOW change to nullable and list all objects requested if null, but require 1-1000 workspace ids
			final String ustr = user == null ? "adminuser" : "user: " + user.getUser();
			getLogger().info(LIST_OBJECTS + " " + ustr);
			return wsmeth.listObjects(params, user);
		}
		if (DELETE_WS.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
			//TODO FEATURE would be better if could provide ID vs. name
			getLogger().info(DELETE_WS + " " + params.getId() + " " + params.getWorkspace());
			ws.setWorkspaceDeleted(null, wksp, true, true);
			return null;
		}
		if (UNDELETE_WS.equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
			//TODO FEATURE would be better if could provide ID vs. name
			getLogger().info(UNDELETE_WS + " " + params.getId() + " " + params.getWorkspace());
			ws.setWorkspaceDeleted(null, wksp, false, true);
			return null;
		}
		if (LIST_WORKSPACE_OWNERS.equals(fn)) {
			getLogger().info(LIST_WORKSPACE_OWNERS);
			return usersToStrings(ws.getAllWorkspaceOwners());
		}
		if (GRANT_MODULE_OWNERSHIP.equals(fn)) {
			final GrantModuleOwnershipParams params = getParams(cmd,
					GrantModuleOwnershipParams.class);
			getLogger().info(GRANT_MODULE_OWNERSHIP + " " + params.getMod() +
					" " + params.getNewOwner());
			wsmeth.grantModuleOwnership(params, null, true);
			return null;
		}
		if (REMOVE_MODULE_OWNERSHIP.equals(fn)) {
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

	private WorkspaceUser getUser(final AdminCommand cmd)
			throws IOException, AuthException {
		return getUser((String) cmd.getUser());
	}

	private WorkspaceUser getUser(final String user)
			throws IOException, AuthException {
		if (user == null) {
			throw new NullPointerException("User may not be null");
		}
		return wsmeth.validateUsers(Arrays.asList(user)).get(0);
	}
	
	private WorkspaceUser getNullableUser(final AdminCommand cmd)
			throws IOException, AuthException {
		if (cmd.getUser() == null) {
			return null;
		}
		return wsmeth.validateUsers(Arrays.asList(cmd.getUser())).get(0);
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
		} catch (JsonMappingException jme) {
			throw new IllegalArgumentException("Unable to deserialize "
					+ clazz.getSimpleName() + " out of params field.", jme);
		}
	}

	//why doesn't this work?
	@SuppressWarnings("unused")
	private <T> T getParams(final Map<String, Object> input) {
		return UObject.transformObjectToObject(input.get("params"),
				new TypeReference<T>() {});
	}
}
