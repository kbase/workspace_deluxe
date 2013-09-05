package us.kbase.workspace.workspaces;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private static final String ALL_USERS = "*";
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
		db.setAllUsersSymbol(ALL_USERS);
	}
	
	private void checkPerms(String user, WorkspaceIdentifier wsi,
			Permission perm, String error) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		if(perm.compareTo(db.getPermission(user, wsi)) > 0) {
			final String err = user == null ? "Anonymous users may not %s workspace %s" :
				"User " + user + " may not %s workspace %s";
			throw new WorkspaceAuthorizationException(String.format(
					err, error, wsi.getIdentifierString()));
		}
	}
	
	public WorkspaceMetaData createWorkspace(String user, String wsname,
			boolean globalread, String description) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public String getWorkspaceDescription(String user, WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		checkPerms(user, wsi, Permission.READ, "read");
		return db.getWorkspaceDescription(wsi);
	}

	public void setPermissions(String user, WorkspaceIdentifier wsi,
			List<String> users, Permission permission) throws
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		checkPerms(user, wsi, Permission.ADMIN, "set permissions on");
		db.setPermissions(wsi, users, permission);
	}

	public Map<String, Permission> getPermissions(String user,
				WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException {
		if (user == null) {
			throw new NullPointerException("user");
		}
		Map<String, Permission> perms = db.getUserAndGlobalPermission(user, wsi);
		if (Permission.ADMIN.compareTo(perms.get(user)) > 0) {
			return perms;
		}
		return db.getAllPermissions(wsi);
	}

	public WorkspaceMetaData getWorkspaceMetaData(String user,
				WorkspaceIdentifier wsi) throws WorkspaceAuthorizationException,
				NoSuchWorkspaceException, WorkspaceCommunicationException {
		checkPerms(user, wsi, Permission.READ, "read");
		return db.getWorkspaceMetadata(user, wsi);
	}
	
	public String getBackendType() {
		return db.getBackendType();
	}
	
	public List<ObjectMetaData> saveObjects(String user,
			WorkspaceObjectCollection objects) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException {
		checkPerms(user, objects.getWorkspaceIdentifier(), Permission.WRITE,
				"write to");
		return db.saveObjects(user, objects);
	}
	
	public static void main(String[] args) throws Exception {
		Database db = new MongoDatabase("localhost", "ws_tester_db1", "foo");
		Workspaces w = new Workspaces(db);
		WorkspaceObjectCollection woc = new WorkspaceObjectCollection(new WorkspaceIdentifier("permspriv"));
//		System.out.println(woc);
		Map<String, Object> data = new HashMap<String, Object>();
		Map<String, Object> meta = new HashMap<String, Object>();
		Map<String, Object> moredata = new HashMap<String, Object>();
		moredata.put("foo", "bar");
		data.put("fubar", moredata);
		meta.put("metastuff", moredata);
		Provenance p = new Provenance("kbasetest2");
		TypeId t = new TypeId("SomeModule", "AType", 0, 1);
		p.addAction(new Provenance.ProvenanceAction().withServiceName("some service"));
		WorkspaceObject wo = new WorkspaceObject(ObjectIdentifier.parseObjectReference("permspriv/32-1"), data, t, meta, p, false);
//		System.out.println(wo);
		woc.addObject(wo);
		woc.addObject(new WorkspaceObject(ObjectIdentifier.parseObjectReference("permspriv/32-2"), data, t, meta, p, false));
//		woc.addObject(new WorkspaceObject(ObjectIdentifier.parseObjectReference("permspriv/myobj2"), data, t, meta, p, false));
		woc.addObject(new WorkspaceObject(new WorkspaceIdentifier("permspriv"), data, t, meta, p, false));
		w.saveObjects("kbasetest2", woc);
	}
}
