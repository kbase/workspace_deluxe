package us.kbase.workspace.kbase.admin;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.IdentifierUtils.processWorkspaceIdentifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.kbase.LocalTypeServerMethods;
import us.kbase.workspace.kbase.WorkspaceDelegator;
import us.kbase.workspace.kbase.WorkspaceDelegator.WorkspaceDelegationException;
import us.kbase.workspace.kbase.WorkspaceServerMethods;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.AdminCommandSpecification;
import us.kbase.workspace.kbase.admin.WorkspaceAdministration.Builder;

/** Builds the standard set of administration command handlers. */
public class AdministrationCommandSetInstaller {
	
	private static final String GET_CONFIG = "getConfig";
	private static final String SET_CONFIG = "setConfig";
	
	private static final String DENY_MOD_REQUEST = "denyModRequest";
	private static final String APPROVE_MOD_REQUEST = "approveModRequest";
	private static final String LIST_MOD_REQUESTS = "listModRequests";

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
	
	private AdministrationCommandSetInstaller() {};
	
	/** Install the handlers.
	 * @param builder an administration interface builder.
	 * @param wsmeth a workspace server methods instance.
	 * @param types a types instance.
	 * @return the updated builder.
	 */
	public static Builder install(
			final WorkspaceAdministration.Builder builder,
			// TODO CODE some of the code here calls the underlying workspace instance.
			// probably better to not do that and just call the service translation layer when
			// possible - e.g. if it's not an admin-only command.
			final WorkspaceServerMethods wsmeth,
			final LocalTypeServerMethods types) {
		
		requireNonNull(wsmeth, "wsmeth");
		installTypeHandlers(requireNonNull(types, "type"), requireNonNull(builder, "builder"));
		installDynamicConfigHandlers(wsmeth, builder);
		installPermissionHandlers(wsmeth, builder);
		installWorkspaceHandlers(wsmeth, builder);
		installListWorkspaceHandlers(wsmeth, builder);
		installDeleteWorkspaceHandlers(wsmeth, builder);
		installObjectHandlers(wsmeth, builder);
		return builder;
	}
	
	/** Install the handlers, delegating type related commands to another workspace service.
	 * @param builder an administration interface builder.
	 * @param wsmeth a workspace server methods instance.
	 * @param delegator a workspace method delegator.
	 * @return the updated builder.
	 */
	public static Builder install(
			final WorkspaceAdministration.Builder builder,
			// see note above about calling the underlying workspace instance.
			final WorkspaceServerMethods wsmeth,
			final WorkspaceDelegator delegator) {

		requireNonNull(wsmeth, "wsmeth");
		installTypeHandlers(
				requireNonNull(delegator, "delegator"), requireNonNull(builder, "builder"));
		installDynamicConfigHandlers(wsmeth, builder);
		installPermissionHandlers(wsmeth, builder);
		installWorkspaceHandlers(wsmeth, builder);
		installListWorkspaceHandlers(wsmeth, builder);
		installDeleteWorkspaceHandlers(wsmeth, builder);
		installObjectHandlers(wsmeth, builder);
		return builder;
	}
	
	private static void installTypeHandlers(
			final WorkspaceDelegator delegator,
			final Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
				LIST_MOD_REQUESTS,
				(cmd, token, toDelete) -> {
					getLogger().info(LIST_MOD_REQUESTS +
							" delegated to " + delegator.getTargetWorkspace());
					return delegate(delegator, cmd, token);
				}));
		builder.withCommand(new AdminCommandSpecification(
			APPROVE_MOD_REQUEST,
			(cmd, token, toDelete) -> {
				getLogger().info(APPROVE_MOD_REQUEST + " " + cmd.getModule() +
						" delegated to " + delegator.getTargetWorkspace());
				return delegate(delegator, cmd, token);
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			DENY_MOD_REQUEST,
			(cmd, token, toDelete) -> {
				getLogger().info(DENY_MOD_REQUEST + " " + cmd.getModule() +
						" delegated to " + delegator.getTargetWorkspace());
				return delegate(delegator, cmd, token);
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			GRANT_MODULE_OWNERSHIP,
			(cmd, token, toDelete) -> {
				final GrantModuleOwnershipParams params = getParams(cmd,
						GrantModuleOwnershipParams.class);
				getLogger().info(GRANT_MODULE_OWNERSHIP + " " + params.getMod() +
						" " + params.getNewOwner() +
						" delegated to " + delegator.getTargetWorkspace());
				return delegate(delegator, cmd, token);
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			REMOVE_MODULE_OWNERSHIP,
			(cmd, token, toDelete) -> {
				final RemoveModuleOwnershipParams params = getParams(cmd,
						RemoveModuleOwnershipParams.class);
				getLogger().info(REMOVE_MODULE_OWNERSHIP + " " + params.getMod() +
						" " + params.getOldOwner() +
						" delegated to " + delegator.getTargetWorkspace());
				return delegate(delegator, cmd, token);
			},
			true));
	}

	public static Object delegate(
			final WorkspaceDelegator delegator,
			final AdminCommand cmd,
			final AuthToken token)
			throws WorkspaceDelegationException {
		final UObject res = delegator.delegate(token, c -> c.administer(new UObject(cmd)));
		return res == null ? null : res.isNull() ? null : res.asClassInstance(Object.class);
	}

	private static void installTypeHandlers(
			final LocalTypeServerMethods types,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			LIST_MOD_REQUESTS,
			(cmd, token, toDelete) -> {
				getLogger().info(LIST_MOD_REQUESTS);
				return types.getTypes().listModuleRegistrationRequests();
			}));
		builder.withCommand(new AdminCommandSpecification(
			APPROVE_MOD_REQUEST,
			(cmd, token, toDelete) -> {
				final String mod = cmd.getModule();
				getLogger().info(APPROVE_MOD_REQUEST + " " + mod);
				types.getTypes().resolveModuleRegistration(mod, true);
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			DENY_MOD_REQUEST,
			(cmd, token, toDelete) -> {
				final String mod = cmd.getModule();
				getLogger().info(DENY_MOD_REQUEST + " " + mod);
				types.getTypes().resolveModuleRegistration(mod, false);
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			GRANT_MODULE_OWNERSHIP,
			(cmd, token, toDelete) -> {
				final GrantModuleOwnershipParams params = getParams(cmd,
						GrantModuleOwnershipParams.class);
				getLogger().info(GRANT_MODULE_OWNERSHIP + " " + params.getMod() +
						" " + params.getNewOwner());
				types.grantModuleOwnership(params, null, true);
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			REMOVE_MODULE_OWNERSHIP,
			(cmd, token, toDelete) -> {
				final RemoveModuleOwnershipParams params = getParams(cmd,
						RemoveModuleOwnershipParams.class);
				getLogger().info(REMOVE_MODULE_OWNERSHIP + " " + params.getMod() +
						" " + params.getOldOwner());
				types.removeModuleOwnership(params, null, true);
				return null;
			},
			true));
	}
	
	private static void installDynamicConfigHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			GET_CONFIG,
			(cmd, token, toDelete) -> {
				getLogger().info(GET_CONFIG);
				return ImmutableMap.of("config", wsmeth.getWorkspace().getConfig().toMap());
			}));
		builder.withCommand(new AdminCommandSpecification(
			SET_CONFIG,
			(cmd, token, toDelete) -> {
				getLogger().info(SET_CONFIG); // add parameters?
				final SetConfigParams params = getParams(cmd, SetConfigParams.class);
				wsmeth.getWorkspace().setConfig(DynamicConfigUpdate.getBuilder()
						.withMap(params.set).build());
				return null;
			},
			true));
	}

	private static void installPermissionHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			SET_PERMISSIONS,
			(cmd, token, toDelete) -> {
				final SetPermissionsParams params = getParams(cmd, SetPermissionsParams.class);
				final long id = wsmeth.setPermissionsAsAdmin(params, token);
				getLogger().info(SET_PERMISSIONS + " " + id + " " + params.getNewPermission() +
						" " + StringUtils.join(params.getUsers(), " "));
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			SET_GLOBAL_PERMISSION,
			(cmd, token, toDelete) -> {
				final WorkspaceUser user = wsmeth.validateUser(cmd.getUser(), token);
				final SetGlobalPermissionsParams params = getParams(cmd,
						SetGlobalPermissionsParams.class);
				final long id = wsmeth.setGlobalPermission(params, user);
				getLogger().info(SET_GLOBAL_PERMISSION + " " + id + " " +
						params.getNewPermission() + " " + user.getUser());
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			GET_PERMISSIONS,
			(cmd, token, toDelete) -> {
				final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
				final WorkspaceUser user = validateNullableUser(wsmeth, cmd, token);
				final Map<String, String> perms;
				if (user == null) {
					perms = wsmeth.getPermissions(Arrays.asList(params), null, true)
							.getPerms().get(0);
				} else {
					perms = wsmeth.getPermissions(params, user);
				}
				//TODO FEATURE would be better if could always provide ID vs. name
				getLogger().info(GET_PERMISSIONS + " " + params.getId() + " " +
						params.getWorkspace() + (user == null ? "" : " " + user.getUser()));
				return perms;
			}));
		builder.withCommand(new AdminCommandSpecification(
			GET_PERMISSIONS_MASS,
			(cmd, token, toDelete) -> {
				final GetPermissionsMassParams params = getParams(
						cmd, GetPermissionsMassParams.class);
				final WorkspacePermissions perms = wsmeth.getPermissions(
						params.getWorkspaces(), null, true);
				// not sure what to log here, could be 1K entries.
				getLogger().info(GET_PERMISSIONS_MASS + " " + params.getWorkspaces().size() +
						" workspaces in input");
				return perms;
			}));
	}
	
	private static void installWorkspaceHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			CREATE_WORKSPACE,
			(cmd, token, toDelete) -> {
				final WorkspaceUser user = wsmeth.validateUser(cmd.getUser(), token);
				final CreateWorkspaceParams params = getParams(cmd, CreateWorkspaceParams.class);
				final Tuple9<Long, String, String, String, Long, String, String, String,
				Map<String, String>> ws =  wsmeth.createWorkspace(params, user);
				getLogger().info(CREATE_WORKSPACE + " " + ws.getE1() + " " + ws.getE3());
				return ws;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			SET_WORKSPACE_OWNER,
			(cmd, token, toDelete) -> {
				final SetWorkspaceOwnerParams params = getParams(
						cmd, SetWorkspaceOwnerParams.class);
				final WorkspaceIdentifier wsi = processWorkspaceIdentifier(params.wsi);
				final WorkspaceInformation info = wsmeth.getWorkspace().setWorkspaceOwner(
						null,
						wsi,
						params.new_user == null ?
								null : wsmeth.validateUser(params.new_user, token),
						Optional.ofNullable(params.new_name), true);
				getLogger().info(SET_WORKSPACE_OWNER + " " + info.getId() + " " +
						info.getOwner().getUser());
				return wsInfoToTuple(info);
			}, 
			true));
		builder.withCommand(new AdminCommandSpecification(
			SET_WORKSPACE_DESCRIPTION,
			(cmd, token, toDelete) -> {
				final SetWorkspaceDescriptionParams params = getParams(
						cmd, SetWorkspaceDescriptionParams.class);
				final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
						params.getWorkspace(), params.getId());
				final long id = wsmeth.getWorkspace().setWorkspaceDescription(
						null, wsi, params.getDescription(), true);
				getLogger().info(SET_WORKSPACE_DESCRIPTION + " " + id);
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			GET_WORKSPACE_DESCRIPTION,
			(cmd, token, toDelete) -> {
				final WorkspaceIdentity wsi = getParams(cmd, WorkspaceIdentity.class);
				final WorkspaceIdentifier wsid = processWorkspaceIdentifier(wsi);
				final String desc = wsmeth.getWorkspace()
						.getWorkspaceDescription(null, wsid, true);
				// TODO FEATURE would be better if could always provide ID vs. name
				getLogger().info(GET_WORKSPACE_DESCRIPTION + " " + wsi.getId() + " " +
						wsi.getWorkspace());
				return desc;
			}));
		builder.withCommand(new AdminCommandSpecification(
			GET_WORKSPACE_INFO,
			(cmd, token, toDelete) -> {
				final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
				final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
								params.getWorkspace(), params.getId());
				final WorkspaceInformation info = wsmeth.getWorkspace().
						getWorkspaceInformationAsAdmin(wsi);
				getLogger().info(GET_WORKSPACE_INFO + " " + info.getId());
				return wsInfoToTuple(info);
			}));
	}

	private static void installObjectHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			SAVE_OBJECTS,
			(cmd, token, toDelete) -> {
				final WorkspaceUser user = wsmeth.validateUser(cmd.getUser(), token);
				final SaveObjectsParams params = getParams(cmd, SaveObjectsParams.class);
				//method has its own logging
				getLogger().info(SAVE_OBJECTS + " " + user.getUser());
				return wsmeth.saveObjects(params, user, token);
			},
			true));
		// TODO CODE for the next 3 methods, is passing the admin user really necessary?
		// Check at some point, maybe remove
		builder.withCommand(new AdminCommandSpecification(
			GET_OBJECT_INFO,
			(cmd, token, toDelete) -> {
				final GetObjectInfo3Params params = getParams(cmd, GetObjectInfo3Params.class);
				getLogger().info(GET_OBJECT_INFO); // method has its own logging
				return wsmeth.getObjectInformation(
						params, new WorkspaceUser(token.getUserName()), true);
			}));
		builder.withCommand(new AdminCommandSpecification(
			GET_OBJECT_HIST,
			(cmd, token, toDelete) -> {
				final ObjectIdentity params = getParams(cmd, ObjectIdentity.class);
				getLogger().info(GET_OBJECT_HIST); // method has its own logging
				return wsmeth.getObjectHistory(
						params, new WorkspaceUser(token.getUserName()), true);
			}));
		builder.withCommand(new AdminCommandSpecification(
			GET_OBJECTS,
			(cmd, token, resourcesToDelete) -> {
				final GetObjects2Params params = getParams(cmd, GetObjects2Params.class);
				getLogger().info(GET_OBJECTS); // method has its own logging
				return wsmeth.getObjects(params, new WorkspaceUser(token.getUserName()), true,
						resourcesToDelete);
			}));
		builder.withCommand(new AdminCommandSpecification(
			LIST_OBJECTS,
			(cmd, token, resourcesToDelete) -> {
				final ListObjectsParams params = getParams(cmd, ListObjectsParams.class);
				final WorkspaceUser user = validateNullableUser(wsmeth, cmd, token);
				final String ustr = user == null ? "adminuser" : "user: " + user.getUser();
				getLogger().info(LIST_OBJECTS + " " + ustr);
				return wsmeth.listObjects(params, user, user == null);
			}));
	}
	
	private static void installListWorkspaceHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			LIST_WORKSPACES,
			(cmd, token, resourcesToDelete) -> {
				final WorkspaceUser user = wsmeth.validateUser(cmd.getUser(), token);
				final ListWorkspaceInfoParams params = getParams(
						cmd, ListWorkspaceInfoParams.class);
				getLogger().info(LIST_WORKSPACES + " " + user.getUser());
				return wsmeth.listWorkspaceInfo(params, user);
			}));
		builder.withCommand(new AdminCommandSpecification(
			LIST_WORKSPACE_IDS,
			(cmd, token, resourcesToDelete) -> {
				final WorkspaceUser user = wsmeth.validateUser(cmd.getUser(), token);
				final ListWorkspaceIDsParams params = getParams(cmd, ListWorkspaceIDsParams.class);
				getLogger().info(LIST_WORKSPACE_IDS + " " + user.getUser());
				return wsmeth.listWorkspaceIDs(params, user);
			}));
		builder.withCommand(new AdminCommandSpecification(
			LIST_WORKSPACE_OWNERS,
			(cmd, token, resourcesToDelete) -> {
				getLogger().info(LIST_WORKSPACE_OWNERS);
				return wsmeth.getWorkspace().getAllWorkspaceOwners().stream().map(u -> u.getUser())
						.collect(Collectors.toList());
			}));
	}

	private static void installDeleteWorkspaceHandlers(
			final WorkspaceServerMethods wsmeth,
			final WorkspaceAdministration.Builder builder) {
		builder.withCommand(new AdminCommandSpecification(
			DELETE_WS,
			(cmd, token, toDelete) -> {
				final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
				final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
				final long id = wsmeth.getWorkspace().setWorkspaceDeleted(null, wksp, true, true);
				getLogger().info(DELETE_WS + " " + id);
				return null;
			},
			true));
		builder.withCommand(new AdminCommandSpecification(
			UNDELETE_WS,
			(cmd, token, toDelete) -> {
				final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
				final WorkspaceIdentifier wksp = processWorkspaceIdentifier(params);
				final long id = wsmeth.getWorkspace().setWorkspaceDeleted(null, wksp, false, true);
				getLogger().info(UNDELETE_WS + " " + id);
				return null;
			},
			true));
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
	
	private static <T> T getParams(final AdminCommand input, final Class<T> clazz)
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
	
	private static Logger getLogger() {
		return LoggerFactory.getLogger(AdministrationCommandSetInstaller.class);
	}
	
	private static WorkspaceUser validateNullableUser(
			final WorkspaceServerMethods wsmeth,
			final AdminCommand cmd,
			final AuthToken token)
			throws IOException, AuthException {
		if (cmd.getUser() == null) {
			return null;
		}
		return wsmeth.validateUser(cmd.getUser(), token);
	}
	
}
