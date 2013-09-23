package us.kbase.workspace.workspaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.AbsoluteTypeId;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
	}
	
	private void comparePermission(final WorkspaceUser user,
			final Permission required, final Permission available,
			final String workspace, final String operation) throws
			WorkspaceAuthorizationException {
		if(required.compareTo(available) > 0) {
			final String err = user == null ?
					"Anonymous users may not %s workspace %s" :
					"User " + user.getUser() + " may not %s workspace %s";
			throw new WorkspaceAuthorizationException(String.format(
					err, operation, workspace));
		}
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		comparePermission(user, perm, db.getPermission(user, wsid),
				wsi.getIdentifierString(), operation);
		return wsid;
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException, CorruptWorkspaceDBException {
		final Set<WorkspaceIdentifier> wsis =
				new HashSet<WorkspaceIdentifier>();
		for (final ObjectIdentifier o: loi) {
			wsis.add(o.getWorkspaceIdentifier());
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(wsis);
		final Map<ResolvedWorkspaceID, Permission> perms =
				db.getPermissions(user,
						new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ret =
				new HashMap<ObjectIdentifier, ObjectIDResolvedWS>();
		for (final ObjectIdentifier o: loi) {
			final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
			comparePermission(user, perm, perms.get(r),
					o.getWorkspaceIdentifierString(), operation);
			ret.put(o, o.resolveWorkspace(r));
		}
		return ret;
	}
	
	public WorkspaceMetaData createWorkspace(final WorkspaceUser user, 
			final String wsname, boolean globalread, String description)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(final WorkspaceUser user,
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceDescription(wsid);
	}

	public void setPermissions(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final List<WorkspaceUser> users,
			final Permission permission) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"set permissions on");
		db.setPermissions(wsid, users, permission);
	}

	public Map<User, Permission> getPermissions(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (user == null) {
			throw new IllegalArgumentException("User cannot be null");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		final Map<User, Permission> perms =
				db.getUserAndGlobalPermission(user, wsid);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsid);
	}

	public WorkspaceMetaData getWorkspaceMetaData(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws
				NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceMetadata(user, wsid);
	}
	
	public String getBackendType() {
		return db.getBackendType();
	}
	
//	private static String getObjectErrorId(final WorkspaceObjectID oi,
//			final int objcount) {
//		String objErrId = "#" + objcount;
//		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
//		return objErrId;
//	}
	
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			final List<WorkspaceSaveObject> objects) throws
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException {
		//TODO this should take objects. Convert to JsonNode, then make new object with the node & abs type.
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		final List<ResolvedSaveObject> saveobjs =
				new ArrayList<ResolvedSaveObject>();
		//this method must maintain the order of the objects
//		int objcount = 1;
		for (WorkspaceSaveObject wo: objects) {
//			final WorkspaceObjectID oi = wo.getObjectIdentifier();
//			final String objErrId = getObjectErrorId(oi, objcount);
//			final String objerrpunc = oi == null ? "" : ",";
			 //TODO replace this with value returned from validator
			final AbsoluteTypeId type = new AbsoluteTypeId(wo.getType().getType(),
					wo.getType().getMajorVersion() == null ? 0 : wo.getType().getMajorVersion(),
					wo.getType().getMinorVersion() == null ? 0 : wo.getType().getMinorVersion());
			//TODO validate objects by type
			//TODO get reference list by object
			saveobjs.add(wo.resolve(type, wo.getData()));//TODO this goes below after resolving ids
//			objcount++;
		}
		//TODO resolve references (std resolve, resolve to IDs, no resolution)
		//TODO make sure all object and provenance references exist aren't deleted, convert to perm refs - batch
		//TODO rewrite references
		//TODO when safe, add references to references collection
		//TODO replace object in workspace object
		return db.saveObjects(user, rwsi, saveobjs);
	}
	
	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException {
		if (loi.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, WorkspaceObjectData> data = 
				db.getObjects(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(data.get(ws.get(o)));
		}
		return ret;
	}
	
	public List<ObjectUserMetaData> getObjectMetaData(WorkspaceUser user,
			List<ObjectIdentifier> loi) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException, NoSuchObjectException {
		if (loi.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, ObjectUserMetaData> meta = 
				db.getObjectMeta(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<ObjectUserMetaData> ret =
				new ArrayList<ObjectUserMetaData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(meta.get(ws.get(o)));
		}
		return ret;
	}
}
