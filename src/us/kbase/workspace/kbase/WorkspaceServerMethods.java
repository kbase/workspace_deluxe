package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.ArgUtils.getGlobalWSPerm;
import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.ArgUtils.processProvenance;
import static us.kbase.workspace.kbase.ArgUtils.longToBoolean;
import static us.kbase.workspace.kbase.ArgUtils.objInfoToTuple;
import static us.kbase.workspace.kbase.ArgUtils.parseDate;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processWorkspaceIdentifier;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class WorkspaceServerMethods {
	
	final private Workspace ws;
	final private Types types;
	final private URL handleServiceUrl;
	final private int maximumIDCount;
	final private ConfigurableAuthService auth;
	
	public WorkspaceServerMethods(
			final Workspace ws,
			final Types types,
			final URL handleServiceUrl,
			final int maximumIDCount,
			final ConfigurableAuthService auth) {
		this.ws = ws;
		this.types = types;
		this.handleServiceUrl = handleServiceUrl;
		this.maximumIDCount = maximumIDCount;
		this.auth = auth;
	}

	public Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>
			createWorkspace(
			final CreateWorkspaceParams params, final WorkspaceUser user)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			MetadataException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		Permission p = getGlobalWSPerm(params.getGlobalread());
		final WorkspaceInformation meta = ws.createWorkspace(user,
				params.getWorkspace(), p.equals(Permission.READ),
				params.getDescription(),
				new WorkspaceUserMetadata(params.getMeta()));
		return wsInfoToTuple(meta);
	}
	
	public void setPermissions(final SetPermissionsParams params,
			final WorkspaceUser user)
			throws IOException, AuthException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		setPermissions(params, user, false);
	}
	
	public void setPermissions(final SetPermissionsParams params,
			final WorkspaceUser user, boolean asAdmin)
			throws IOException, AuthException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final Permission p = translatePermission(params.getNewPermission());
		if (params.getUsers().size() == 0) {
			throw new IllegalArgumentException("Must provide at least one user");
		}
		final List<WorkspaceUser> users = validateUsers(params.getUsers());
		ws.setPermissions(user, wsi, users, p, asAdmin);
	}
	
	public List<WorkspaceUser> validateUsers(final List<String> users)
			throws IOException, AuthException {
		final List<WorkspaceUser> wsusers = ArgUtils.convertUsers(users);
		final Map<String, Boolean> userok;
		try {
			userok = auth.isValidUserName(users);
		} catch (UnknownHostException uhe) {
			//message from UHE is only the host name
			throw new AuthException(
					"Could not contact Authorization Service host to validate user names: "
							+ uhe.getMessage(), uhe);
		}
		for (String u: userok.keySet()) {
			if (!userok.get(u)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", u));
			}
		}
		return wsusers;
	}

	public void setGlobalPermission(final SetGlobalPermissionsParams params,
			WorkspaceUser user)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceAuthorizationException, WorkspaceCommunicationException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final Permission p = translatePermission(params.getNewPermission());
		ws.setGlobalPermission(user, wsi, p);
	}
	
	public Map<String, String> getPermissions(WorkspaceIdentity wsi,
			WorkspaceUser user)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		return getPermissions(Arrays.asList(wsi), user).getPerms().get(0);
	}
	
	public WorkspacePermissions getPermissions(
			List<WorkspaceIdentity> workspaces, WorkspaceUser user)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		final List<WorkspaceIdentifier> wsil =
				new LinkedList<WorkspaceIdentifier>();
		for (final WorkspaceIdentity wsi: workspaces) {
			wsil.add(processWorkspaceIdentifier(wsi));
		}
		final List<Map<User, Permission>> perms = ws.getPermissions(user, wsil);
		final List<Map<String, String>> ret =
				new LinkedList<Map<String,String>>();
		for (final Map<User, Permission> acls: perms){
			final Map<String, String> inner = new HashMap<String, String>();
			for (User acl: acls.keySet()) {
				inner.put(acl.getUser(), translatePermission(acls.get(acl)));
			}
			ret.add(inner);
		}
		return new WorkspacePermissions().withPerms(ret);
	}

	public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> saveObjects(
			final SaveObjectsParams params,
			final WorkspaceUser user,
			final AuthToken token)
			throws ParseException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException, NoSuchObjectException,
			CorruptWorkspaceDBException, NoSuchWorkspaceException,
			TypedObjectValidationException, TypeStorageException,
			IOException, TypedObjectSchemaException {

		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final List<WorkspaceSaveObject> woc = new ArrayList<WorkspaceSaveObject>();
		int count = 1;
		if (params.getObjects().isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		for (ObjectSaveData d: params.getObjects()) {
			checkAddlArgs(d.getAdditionalProperties(), d.getClass());
			ObjectIDNoWSNoVer oi = null;
			if (d.getName() != null || d.getObjid() != null) {
				 oi = ObjectIDNoWSNoVer.create(d.getName(), d.getObjid());
			}
			String errprefix = "Object ";
			if (oi == null) {
				errprefix += count;
			} else {
				errprefix += count + ", " + oi.getIdentifierString() + ",";
			}
			if (d.getData() == null) {
				throw new IllegalArgumentException(errprefix + " has no data");
			}
			TypeDefId t;
			try {
				t = TypeDefId.fromTypeString(d.getType());
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(errprefix + " type error: "
						+ iae.getLocalizedMessage(), iae);
			}
			final Provenance p = processProvenance(user,
					d.getProvenance());
			final boolean hidden = longToBoolean(d.getHidden());
			try {
				if (oi == null) {
					woc.add(new WorkspaceSaveObject(d.getData(), t,
							new WorkspaceUserMetadata(d.getMeta()), p,
							hidden));
				} else {
					woc.add(new WorkspaceSaveObject(oi, d.getData(), t, 
							new WorkspaceUserMetadata(d.getMeta()), p,
							hidden));
				}
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(errprefix + " save error: "
						+ iae.getLocalizedMessage(), iae);
			} catch (MetadataException me) {
				throw new IllegalArgumentException(errprefix + " save error: "
						+ me.getLocalizedMessage(), me);
			}
			count++;
		}
		params.setObjects(null); 
		final IdReferenceHandlerSetFactory fac =
				new IdReferenceHandlerSetFactory(maximumIDCount);
		fac.addFactory(new HandleIdHandlerFactory(handleServiceUrl,
				token));
		
		final List<ObjectInformation> meta = ws.saveObjects(user, wsi, woc, fac); 
		return objInfoToTuple(meta, true);
	}
	
	public void grantModuleOwnership(final GrantModuleOwnershipParams params,
			final WorkspaceUser user, boolean asAdmin)
			throws TypeStorageException, NoSuchPrivilegeException {
		checkAddlArgs(params.getAdditionalProperties(),
				GrantModuleOwnershipParams.class);
		types.grantModuleOwnership(params.getMod(), params.getNewOwner(),
				longToBoolean(params.getWithGrantOption()), user, asAdmin);
	}

	public void removeModuleOwnership(final RemoveModuleOwnershipParams params,
			final WorkspaceUser user, final boolean asAdmin)
			throws NoSuchPrivilegeException, TypeStorageException {
		checkAddlArgs(params.getAdditionalProperties(),
				RemoveModuleOwnershipParams.class);
		types.removeModuleOwnership(params.getMod(), params.getOldOwner(),
				user, asAdmin);
	}

	public List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>>>
			listWorkspaceInfo(final ListWorkspaceInfoParams params,
			final WorkspaceUser user)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			ParseException, MetadataException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final Permission p = params.getPerm() == null ? null :
				translatePermission(params.getPerm());
		return wsInfoToTuple(ws.listWorkspaces(user,
				p, ArgUtils.convertUsers(params.getOwners()),
				new WorkspaceUserMetadata(params.getMeta()),
				parseDate(params.getAfter()),
				parseDate(params.getBefore()),
				longToBoolean(params.getExcludeGlobal()),
				longToBoolean(params.getShowDeleted()),
				longToBoolean(params.getShowOnlyDeleted())));
	}
}
