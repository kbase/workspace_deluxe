package us.kbase.workspace.workspaces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
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
	
	private void checkPerms(WorkspaceUser user, WorkspaceIdentifier wsi,
			Permission perm, String error) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			CorruptWorkspaceDBException {
		if(perm.compareTo(db.getPermission(user, wsi)) > 0) {
			final String err = user == null ? "Anonymous users may not %s workspace %s" :
				"User " + user.getUser() + " may not %s workspace %s";
			throw new WorkspaceAuthorizationException(String.format(
					err, error, wsi.getIdentifierString()));
		}
	}
	
	public WorkspaceMetaData createWorkspace(WorkspaceUser user, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(WorkspaceUser user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		checkPerms(user, wsi, Permission.READ, "read");
		return db.getWorkspaceDescription(wsi);
	}

	public void setPermissions(WorkspaceUser user, WorkspaceIdentifier wsi,
			List<WorkspaceUser> users, Permission permission) throws
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		checkPerms(user, wsi, Permission.ADMIN, "set permissions on");
		db.setPermissions(wsi, users, permission);
	}

	public Map<User, Permission> getPermissions(WorkspaceUser user,
				WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (user == null) {
			throw new NullPointerException("user");
		}
		Map<User, Permission> perms = db.getUserAndGlobalPermission(user, wsi);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsi);
	}

	public WorkspaceMetaData getWorkspaceMetaData(WorkspaceUser user,
				WorkspaceIdentifier wsi) throws WorkspaceAuthorizationException,
				NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		checkPerms(user, wsi, Permission.READ, "read");
		return db.getWorkspaceMetadata(user, wsi);
	}
	
	public String getBackendType() {
		return db.getBackendType();
	}
	
	public List<ObjectMetaData> saveObjects(WorkspaceUser user,
			WorkspaceObjectCollection objects) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException {
		checkPerms(user, objects.getWorkspaceIdentifier(), Permission.WRITE,
				"write to");
		return db.saveObjects(user, objects);
	}
	
	public void getObjects(WorkspaceUser user, List<ObjectIdentifier> loi) {
		// TODO Auto-generated method stub
		
	}
	
	public static void main(String[] args) throws Exception {
		Database db = new MongoDatabase("localhost", "ws_tester_db1", "foo");
		Workspaces w = new Workspaces(db);
//		db.createWorkspace("kbasetest", "permspriv", false, "foo");
//		db.setPermissions(new WorkspaceIdentifier("permspriv"), Arrays.asList("kbasetest2"), Permission.WRITE);
		WorkspaceObjectCollection woc = new WorkspaceObjectCollection(new WorkspaceIdentifier("permspriv"));
//		System.out.println(woc);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> meta = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", moredata);
		Provenance p = new Provenance("kbasetest2");
		TypeId t = new TypeId(new WorkspaceType("SomeModule", "AType"), 0, 1);
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		WorkspaceSaveObject wo = new WorkspaceSaveObject(ObjectIdentifier.parseObjectReference("permspriv/29-1"), data, t, meta, p, false);
//		System.out.println(wo);
		woc.addObject(wo);
		woc.addObject(new WorkspaceSaveObject(ObjectIdentifier.parseObjectReference("permspriv/29-1"), data, t, meta, p, false));
		woc.addObject(new WorkspaceSaveObject(ObjectIdentifier.parseObjectReference("permspriv/29-2"), data, t, meta, p, false));
//		woc.addObject(new WorkspaceObject(ObjectIdentifier.parseObjectReference("permspriv/myobj2"), data, t, meta, p, false));
		woc.addObject(new WorkspaceSaveObject(new WorkspaceIdentifier("permspriv"), data, t, meta, p, false));
		List<ObjectMetaData> objmeta = w.saveObjects(new WorkspaceUser("kbasetest2"), woc);
		System.out.println("\n***** results****");
		System.out.println(objmeta);
	}

}
