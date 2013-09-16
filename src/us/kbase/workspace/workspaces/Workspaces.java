package us.kbase.workspace.workspaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceObjectID;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.mongo.MongoDatabase;
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
	
	public List<ObjectMetaData> saveObjects(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			final List<WorkspaceSaveObject> objects) throws
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException {
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		return db.saveObjects(user, rwsi, objects);
	}
	
	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		return db.getObjects(checkPerms(user, loi, Permission.READ, "read"));
		
	}
	
	private List<ObjectIDResolvedWS> checkPerms(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final Permission perm,
			final String operation) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException {
		final Set<WorkspaceIdentifier> wsis =
				new HashSet<WorkspaceIdentifier>();
		for (ObjectIdentifier o: loi) {
			wsis.add(o.getWorkspaceIdentifier());
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(wsis);
		final Map<ResolvedWorkspaceID, Permission> perms =
				db.getPermissions(user,
						new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		final List<ObjectIDResolvedWS> ret = new ArrayList<ObjectIDResolvedWS>();
		for (final ObjectIdentifier o: loi) {
			final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
			comparePermission(user, perm, perms.get(r),
					o.getWorkspaceIdentifierString(), operation);
			ret.add(o.resolveWorkspace(r));
		}
		return ret;
	}
	
	public static void main(String[] args) throws Exception {
		Database db = new MongoDatabase("localhost", "ws_tester_db1", "foo");
		Workspaces w = new Workspaces(db);
//		db.createWorkspace("kbasetest", "permspriv", false, "foo");
//		db.setPermissions(new WorkspaceIdentifier("permspriv"), Arrays.asList("kbasetest2"), Permission.WRITE);
		WorkspaceIdentifier wsi = new WorkspaceIdentifier("permspriv");
//		System.out.println(woc);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, String> meta = new HashMap<String, String>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", "meta");
		Provenance p = new Provenance("kbasetest2");
		TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		WorkspaceSaveObject wo = new WorkspaceSaveObject(new WorkspaceObjectID("29-1"), data, t, meta, p, false);
//		System.out.println(wo);
		List<WorkspaceSaveObject> woc = new ArrayList<WorkspaceSaveObject>();
		woc.add(wo);
		woc.add(new WorkspaceSaveObject(new WorkspaceObjectID("29-1"), data, t, meta, p, false));
		woc.add(new WorkspaceSaveObject(new WorkspaceObjectID("29-2"), data, t, meta, p, false));
//		woc.addObject(new WorkspaceObject(ObjectIdentifier.parseObjectReference("permspriv/myobj2"), data, t, meta, p, false));
		woc.add(new WorkspaceSaveObject(data, t, meta, p, false));
		List<ObjectMetaData> objmeta = w.saveObjects(new WorkspaceUser("kbasetest2"), wsi, woc);
		System.out.println("\n***** results****");
		System.out.println(objmeta);
	}

}
