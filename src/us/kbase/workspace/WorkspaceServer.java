package us.kbase.workspace;

import java.util.List;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple6;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;

//BEGIN_HEADER
import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.ArgUtils.getUser;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_READ;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_NONE;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processObjectIdentifiers;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processWorkspaceIdentifier;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.auth.AuthService;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectID;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.kbase.ArgUtils;
import us.kbase.workspace.kbase.WorkspaceAdministration;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.Workspaces;
//END_HEADER

/**
 * <p>Original spec-file module name: Workspace</p>
 * <pre>
 * The workspace service at its core is a storage and retrieval system for 
 * typed objects. Objects are organized by the user into one or more workspaces.
 * Features:
 * Versioning of objects
 * Data provenenance
 * Object to object references
 * Workspace sharing
 * ***Add stuff here***
 * Notes about deletion and GC
 * BINARY DATA:
 * All binary data must be hex encoded prior to storage in a workspace. 
 * Attempting to send binary data via a workspace client will cause errors.
 * </pre>
 */
public class WorkspaceServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
	//TODO java doc - really low priority, sorry
	
	private ArgUtils au = new ArgUtils();
	//required deploy parameters:
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	//required backend param:
	private static final String BACKEND_SECRET = "backend-secret"; 
	//auth params:
	private static final String USER = "mongodb-user";
	private static final String PWD = "mongodb-pwd";
	
	private static Map<String, String> wsConfig = null;
	
	private final Workspaces ws;
	private final WorkspaceAdministration wsadmin;
	
	private Database getDB(final String host, final String dbs,
			final String secret, final String user, final String pwd) {
		try {
			if (user != null) {
				return new MongoDatabase(host, dbs, secret, user, pwd);
			} else {
				return new MongoDatabase(host, dbs, secret);
			}
		} catch (UnknownHostException uhe) {
			fail("Couldn't find mongo host " + host + ": " +
					uhe.getLocalizedMessage());
		} catch (IOException io) {
			fail("Couldn't connect to mongo host " + host + ": " +
					io.getLocalizedMessage());
		} catch (MongoAuthException ae) {
			fail("Not authorized: " + ae.getLocalizedMessage());
		} catch (InvalidHostException ihe) {
			fail(host + " is an invalid database host: "  +
					ihe.getLocalizedMessage());
		} catch (WorkspaceDBException uwde) {
			fail("The workspace database is invalid: " +
					uwde.getLocalizedMessage());
		}
		return null;
	}
	
	private void fail(final String error) {
		logErr(error);
		System.err.println(error);
		startupFailed();
	}
    //END_CLASS_HEADER

    public WorkspaceServer() throws Exception {
        super("Workspace");
        //BEGIN_CONSTRUCTOR
		//assign config once per jvm, otherwise you could wind up with
		//different threads talking to different mongo instances
		//E.g. first thread's config applies to all threads.
		if (wsConfig == null) {
			wsConfig = new HashMap<String, String>();
			wsConfig.putAll(super.config);
		}
		boolean failed = false;
		if (!wsConfig.containsKey(HOST)) {
			fail("Must provide param " + HOST + " in config file");
			failed = true;
		}
		final String host = wsConfig.get(HOST);
		if (!wsConfig.containsKey(DB)) {
			fail("Must provide param " + DB + " in config file");
			failed = true;
		}
		final String dbs = wsConfig.get(DB);
		if (!wsConfig.containsKey(BACKEND_SECRET)) {
			failed = true;
			fail("Must provide param " + BACKEND_SECRET + " in config file");
		}
		final String secret = wsConfig.get(BACKEND_SECRET);
		if (wsConfig.containsKey(USER) ^ wsConfig.containsKey(PWD)) {
			fail(String.format("Must provide both %s and %s ",
					USER, PWD) + "params in config file if authentication " + 
					"is to be used");
			failed = true;
		}
		if (failed) {
			fail("Server startup failed - all calls will error out.");
			ws = null;
			wsadmin = null;
		} else {
			final String user = wsConfig.get(USER);
			final String pwd = wsConfig.get(PWD);
			String params = "";
			for (String s: Arrays.asList(HOST, DB, USER)) {
				if (wsConfig.containsKey(s)) {
					params += s + "=" + wsConfig.get(s) + "\n";
				}
			}
			params += BACKEND_SECRET + "=[redacted for your safety and comfort]\n";
			if (pwd != null) {
				params += PWD + "=[redacted for your safety and comfort]\n";
			}
			System.out.println("Using connection parameters:\n" + params);
			logInfo("Using connection parameters:\n" + params);
			final Database db = getDB(host, dbs, secret, user, pwd);
			if (db == null) {
				fail("Server startup failed - all calls will error out.");
				ws = null;
				wsadmin = null;
			} else {
				System.out.println(String.format("Initialized %s backend", db.getBackendType()));
				logInfo(String.format("Initialized %s backend", db.getBackendType()));
				ws = new Workspaces(db);
				wsadmin = new WorkspaceAdministration(ws);
			}
		}
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams}
     * @return   parameter "metadata" of original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 6: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" is acceptable.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-5000 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.)
     */
    @JsonServerMethod(rpc = "Workspace.create_workspace")
    public Tuple6<Integer, String, String, String, String, String> createWorkspace(CreateWorkspaceParams params, AuthToken authPart) throws Exception {
        Tuple6<Integer, String, String, String, String, String> returnVal = null;
        //BEGIN create_workspace
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		Permission p = Permission.NONE;
		if (params.getGlobalread() != null) {
			if (!params.getGlobalread().equals(PERM_READ) && !params.getGlobalread().equals(PERM_NONE)) {
				throw new IllegalArgumentException(String.format(
						"globalread must be %s or %s", PERM_NONE, PERM_READ));
			}
			p = translatePermission(params.getGlobalread());
		}
		final WorkspaceMetaData meta = ws.createWorkspace(getUser(authPart),
				params.getWorkspace(), p.equals(Permission.READ),
				params.getDescription());
		returnVal = au.wsMetaToTuple(meta);
        //END create_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_metadata</p>
     * <pre>
     * Get a workspace's metadata.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "meta" of original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 6: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" is acceptable.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-5000 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.)
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_metadata", authOptional=true)
    public Tuple6<Integer, String, String, String, String, String> getWorkspaceMetadata(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        Tuple6<Integer, String, String, String, String, String> returnVal = null;
        //BEGIN get_workspace_metadata
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		final WorkspaceMetaData meta = ws.getWorkspaceMetaData(
				getUser(authPart), wksp);
		returnVal = au.wsMetaToTuple(meta);
        //END get_workspace_metadata
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "description" of String
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_description", authOptional=true)
    public String getWorkspaceDescription(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        String returnVal = null;
        //BEGIN get_workspace_description
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		returnVal = ws.getWorkspaceDescription(getUser(authPart), wksp);
        //END get_workspace_description
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams}
     */
    @JsonServerMethod(rpc = "Workspace.set_permissions")
    public void setPermissions(SetPermissionsParams params, AuthToken authPart) throws Exception {
        //BEGIN set_permissions
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final Permission p = translatePermission(params.getNewPermission());
		if (params.getUsers().size() == 0) {
			throw new IllegalArgumentException("Must provide at least one user");
		}
		final List<WorkspaceUser> users = new ArrayList<WorkspaceUser>();
		for (String u: params.getUsers()) {
			users.add(new WorkspaceUser(u));
		}
		final Map<String, Boolean> userok = AuthService.isValidUserName(
				params.getUsers(), authPart);
		for (String user: userok.keySet()) {
			if (!userok.get(user)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", user));
			}
		}
		ws.setPermissions(getUser(authPart), wsi, users, p);
        //END set_permissions
    }

    /**
     * <p>Original spec-file function name: get_permissions</p>
     * <pre>
     * Get permissions for a workspace.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "perms" of mapping from original type "username" (Login name of a KBase user account.) to original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.)
     */
    @JsonServerMethod(rpc = "Workspace.get_permissions")
    public Map<String,String> getPermissions(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN get_permissions
		returnVal = new HashMap<String, String>(); 
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		final Map<User, Permission> acls = ws.getPermissions(
				getUser(authPart), wksp);
		for (User acl: acls.keySet()) {
			returnVal.put(acl.getUser(), translatePermission(acls.get(acl)));
		}
        //END get_permissions
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace. Saving over a deleted object undeletes
     * it.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams}
     * @return   parameter "meta" of list of original type "object_metadata" (Metadata associated with an object. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp create_date - the creation date of the object. obj_ver ver - the version of the object. username created_by - the user that created the object. ws_id wsid - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes.) &rarr; tuple of size 9: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]. See type_id and type_ver.), parameter "create_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-5000 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Integer, parameter "created_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "chsum" of String, parameter "size" of Integer
     */
    @JsonServerMethod(rpc = "Workspace.save_objects")
    public List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>> saveObjects(SaveObjectsParams params, AuthToken authPart) throws Exception {
        List<Tuple9<Integer, String, String, String, Integer, String, Integer, String, Integer>> returnVal = null;
        //BEGIN save_objects
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(params.getWorkspace(), params.getId());
//		final WorkspaceObjectCollection woc = new WorkspaceObjectCollection(wsi);
		final List<WorkspaceSaveObject> woc = new ArrayList<WorkspaceSaveObject>();
		int count = 1;
		if (params.getObjects().isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		for (ObjectSaveData d: params.getObjects()) {
			checkAddlArgs(d.getAdditionalProperties(), d.getClass());
			WorkspaceObjectID oi = null;
			if (d.getName() != null || d.getObjid() != null) {
				 oi = WorkspaceObjectID.create(d.getName(), d.getObjid());
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
				t = new TypeDefId(d.getType(), d.getTver());
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(errprefix + " type error: "
						+ iae.getLocalizedMessage(), iae);
			}
			final Provenance p = ArgUtils.processProvenance(
					authPart.getUserName(), d.getProvenance());
			final boolean hidden = d.getHidden() != null && d.getHidden() != 0;
			try {
				if (oi == null) {
					woc.add(new WorkspaceSaveObject(d.getData().asJsonNode(),
							t, d.getMetadata(), p, hidden));
				} else {
					woc.add(new WorkspaceSaveObject(oi,
							d.getData().asJsonNode(), t, d.getMetadata(), p,
							hidden));
				}
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(errprefix + " save error: "
						+ iae.getLocalizedMessage(), iae);
			}
			count++;
		}
		
		List<ObjectMetaData> meta = ws.saveObjects(getUser(authPart), wsi, woc); 
		returnVal = au.objMetaToTuple(meta);
        //END save_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_objects</p>
     * <pre>
     * Get objects from the workspace.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectData ObjectData}
     */
    @JsonServerMethod(rpc = "Workspace.get_objects", authOptional=true)
    public List<ObjectData> getObjects(List<ObjectIdentity> objectIds, AuthToken authPart) throws Exception {
        List<ObjectData> returnVal = null;
        //BEGIN get_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = au.translateObjectData(
				ws.getObjects(getUser(authPart), loi));
        //END get_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_metadata</p>
     * <pre>
     * Get object metadata from the workspace.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "meta" of list of original type "object_metadata_full" (Metadata associated with an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp create_date - the creation date of the object. obj_ver ver - the version of the object. username created_by - the user that created the object. ws_id wsid - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta metadata - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 10: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]. See type_id and type_ver.), parameter "create_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-5000 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Integer, parameter "created_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "chsum" of String, parameter "size" of Integer, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.get_object_metadata", authOptional=true)
    public List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>> getObjectMetadata(List<ObjectIdentity> objectIds, AuthToken authPart) throws Exception {
        List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,String>>> returnVal = null;
        //BEGIN get_object_metadata
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = au.objUserMetaToTuple(
				ws.getObjectMetaData(getUser(authPart), loi));
        //END get_object_metadata
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: delete_objects</p>
     * <pre>
     * Delete objects. All versions of an object are deleted, regardless of
     * the version specified in the ObjectIdentity. If an object is already
     * deleted, no error is thrown.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.delete_objects")
    public void deleteObjects(List<ObjectIdentity> objectIds, AuthToken authPart) throws Exception {
        //BEGIN delete_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsDeleted(getUser(authPart), loi, true);
        //END delete_objects
    }

    /**
     * <p>Original spec-file function name: undelete_objects</p>
     * <pre>
     * Undelete objects. All versions of an object are undeleted, regardless
     * of the version specified in the ObjectIdentity. If an object is not
     * deleted, no error is thrown.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.undelete_objects")
    public void undeleteObjects(List<ObjectIdentity> objectIds, AuthToken authPart) throws Exception {
        //BEGIN undelete_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsDeleted(getUser(authPart), loi, false);
        //END undelete_objects
    }

    /**
     * <p>Original spec-file function name: delete_workspace</p>
     * <pre>
     * Delete a workspace. All objects contained in the workspace are deleted.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.delete_workspace")
    public void deleteWorkspace(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        //BEGIN delete_workspace
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		ws.setWorkspaceDeleted(getUser(authPart), wksp, true);
        //END delete_workspace
    }

    /**
     * <p>Original spec-file function name: undelete_workspace</p>
     * <pre>
     * Undelete a workspace. All objects contained in the workspace are
     * undeleted, regardless of their state at the time the workspace was
     * deleted.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.undelete_workspace")
    public void undeleteWorkspace(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        //BEGIN undelete_workspace
    	final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		ws.setWorkspaceDeleted(getUser(authPart), wksp, false);
        //END undelete_workspace
    }

    /**
     * <p>Original spec-file function name: request_module_ownership</p>
     * <pre>
     * Request ownership of a module name.
     * </pre>
     * @param   mod   instance of original type "modulename" (The module name of a KIDL typespec.)
     */
    @JsonServerMethod(rpc = "Workspace.request_module_ownership")
    public void requestModuleOwnership(String mod, AuthToken authPart) throws Exception {
        //BEGIN request_module_ownership
		ws.requestModuleRegistration(getUser(authPart), mod);
        //END request_module_ownership
    }

    /**
     * <p>Original spec-file function name: compile_typespec</p>
     * <pre>
     * Compile a new typespec or recompile an existing typespec.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CompileTypespecParams CompileTypespecParams}
     * @return   instance of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]. See type_id and type_ver.) to original type "jsonschema" (The JSON Schema for a type.)
     */
    @JsonServerMethod(rpc = "Workspace.compile_typespec")
    public Map<String,String> compileTypespec(CompileTypespecParams params, AuthToken authPart) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN compile_typespec
		checkAddlArgs(params.getAdditionalProperties(),
				CompileTypespecParams.class);
		if (!(params.getMod() == null) ^ (params.getSpec() == null)) {
			throw new IllegalArgumentException(
					"Must provide either a spec or module name");
		}
		final Map<String, Long> deps = new HashMap<String, Long>();
		if (params.getDependencies() != null) {
			for (final String module: params.getDependencies().keySet()) {
				try {
					deps.put(module, Long.parseLong(
							params.getDependencies().get(module)));
				} catch (NumberFormatException nfe) {
					throw new IllegalArgumentException(String.format(
							"Version %s for module %s is invalid", module,
							params.getDependencies().get(module)));
				}
			}
		}
		final List<String> add = params.getNewTypes() != null ?
				params.getNewTypes() : new ArrayList<String>();
		final List<String> rem = params.getRemoveTypes() != null ?
				params.getRemoveTypes() : new ArrayList<String>();
		final Map<TypeDefName, TypeChange> res;
		if (params.getMod() != null) {
			 res = ws.compileTypeSpec(
					getUser(authPart), params.getMod(),
					params.getNewTypes(), params.getRemoveTypes(),
					deps, params.getDryrun() != 0);
		} else {
			res = ws.compileNewTypeSpec(getUser(authPart), params.getSpec(),
					add, rem, deps, params.getDryrun() == null ? true : params.getDryrun() != 0);
		}
		returnVal = new HashMap<String, String>();
		for (final TypeChange tc: res.values()) {
			returnVal.put(tc.getTypeVersion().getTypeString(),
					tc.getJsonSchema());
		}
        //END compile_typespec
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_modules</p>
     * <pre>
     * List all typespec modules.
     * </pre>
     * @return   parameter "modules" of list of original type "modulename" (The module name of a KIDL typespec.)
     */
    @JsonServerMethod(rpc = "Workspace.list_modules")
    public List<String> listModules() throws Exception {
        List<String> returnVal = null;
        //BEGIN list_modules
		returnVal = ws.listModules();
        //END list_modules
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_typespec</p>
     * <pre>
     * Get a typespec.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetTypespecParams GetTypespecParams}
     * @return   parameter "spec" of original type "typespec" (A KBase Interface Definition Language (KIDL) typespec.)
     */
    @JsonServerMethod(rpc = "Workspace.get_typespec")
    public String getTypespec(GetTypespecParams params) throws Exception {
        String returnVal = null;
        //BEGIN get_typespec
        //END get_typespec
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_jsonschema</p>
     * <pre>
     * Get JSON schema for a type.
     * </pre>
     * @param   type   instance of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]. See type_id and type_ver.)
     * @return   parameter "schema" of original type "jsonschema" (The JSON Schema for a type.)
     */
    @JsonServerMethod(rpc = "Workspace.get_jsonschema")
    public String getJsonschema(String type) throws Exception {
        String returnVal = null;
        //BEGIN get_jsonschema
        //END get_jsonschema
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: administer</p>
     * <pre>
     * The administration interface.
     * </pre>
     * @param   command   instance of unspecified object
     * @return   parameter "response" of unspecified object
     */
    @JsonServerMethod(rpc = "Workspace.administer")
    public UObject administer(UObject command, AuthToken authPart) throws Exception {
        UObject returnVal = null;
        //BEGIN administer
        returnVal = new UObject(wsadmin.runCommand(authPart, command.asInstance()));
        //END administer
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new WorkspaceServer().startupServer(Integer.parseInt(args[0]));
    }
}
