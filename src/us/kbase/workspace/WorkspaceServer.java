package us.kbase.workspace;

import java.util.List;
import java.util.Map;
import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple10;
import us.kbase.Tuple6;
import us.kbase.UObject;
import us.kbase.auth.AuthToken;

//BEGIN_HEADER
import static us.kbase.workspace.kbase.ArgUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_READ;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_NONE;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processObjectIdentifier;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processWorkspaceIdentifier;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.auth.AuthService;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.kbase.ArgUtils;
import us.kbase.workspace.kbase.KBaseIdentifierFactory;
import us.kbase.workspace.workspaces.ObjectIdentifier;
import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.WorkspaceObjectCollection;
import us.kbase.workspace.workspaces.WorkspaceType;
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
 * BINARY DATA:
 * All binary data must be hex encoded prior to storage in a workspace. 
 * Attempting to send binary data via a workspace client will cause errors.
 * </pre>
 */
public class WorkspaceServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
	//TODO java doc - really low priority, sorry
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
	
	private Database getDB(final String host, final String dbs, final String secret,
			final String user, final String pwd) {
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
		} catch (DBAuthorizationException ae) {
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
			} else {
				System.out.println(String.format("Initialized %s backend", db.getBackendType()));
				logInfo(String.format("Initialized %s backend", db.getBackendType()));
				ws = new Workspaces(db);
			}
		}
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   Original type "CreateWorkspaceParams" (see {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.)
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
		final WorkspaceMetaData meta = ws.createWorkspace(authPart.getUserName(), params.getWorkspace(),
				p.equals(Permission.READ), params.getDescription());
		returnVal = ArgUtils.wsMetaToTuple(meta);
        //END create_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_metadata</p>
     * <pre>
     * Get a workspace's metadata.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.)
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_metadata", authOptional=true)
    public Tuple6<Integer, String, String, String, String, String> getWorkspaceMetadata(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        Tuple6<Integer, String, String, String, String, String> returnVal = null;
        //BEGIN get_workspace_metadata
		checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		final WorkspaceMetaData meta = ws.getWorkspaceMetaData(ArgUtils.getUserName(authPart), wksp);
		returnVal = ArgUtils.wsMetaToTuple(meta);
        //END get_workspace_metadata
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_description", authOptional=true)
    public String getWorkspaceDescription(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        String returnVal = null;
        //BEGIN get_workspace_description
		checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		returnVal = ws.getWorkspaceDescription(ArgUtils.getUserName(authPart), wksp);
        //END get_workspace_description
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   Original type "SetPermissionsParams" (see {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams} for details)
     */
    @JsonServerMethod(rpc = "Workspace.set_permissions")
    public void setPermissions(SetPermissionsParams params, AuthToken authPart) throws Exception {
        //BEGIN set_permissions
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		if (translatePermission(params.getNewPermission()) == null) {
			throw new IllegalArgumentException("Invalid permission: " + params.getNewPermission());
		}
		if (params.getUsers().size() == 0) {
			throw new IllegalArgumentException("Must provide at least one user");
		}
		final Map<String, Boolean> userok = AuthService.isValidUserName(
				params.getUsers(), authPart);
		for (String user: userok.keySet()) {
			if (!userok.get(user)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", user));
			}
		}
		ws.setPermissions(authPart.getUserName(), wsi, params.getUsers(),
				translatePermission(params.getNewPermission()));
        //END set_permissions
    }

    /**
     * <p>Original spec-file function name: get_permissions</p>
     * <pre>
     * Get permissions for a workspace.
     * </pre>
     * @param   wsi   Original type "WorkspaceIdentity" (see {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity} for details)
     */
    @JsonServerMethod(rpc = "Workspace.get_permissions")
    public Map<String,String> getPermissions(WorkspaceIdentity wsi, AuthToken authPart) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN get_permissions
		checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		returnVal = new HashMap<String, String>(); 
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		final Map<String, Permission> acls = ws.getPermissions(authPart.getUserName(), wksp);
		for (String acl: acls.keySet()) {
			returnVal.put(acl, translatePermission(acls.get(acl)));
		}
        //END get_permissions
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace.
     * </pre>
     * @param   params   Original type "SaveObjectsParams" (see {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams} for details)
     */
    @JsonServerMethod(rpc = "Workspace.save_objects")
    public List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,UObject>>> saveObjects(SaveObjectsParams params, AuthToken authPart) throws Exception {
        List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,UObject>>> returnVal = null;
        //BEGIN save_objects
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(params.getWorkspace(), params.getId());
		final WorkspaceObjectCollection woc = new WorkspaceObjectCollection(wsi);
		int count = 1;
		for (ObjectSaveData d: params.getObjects()) {
			checkAddlArgs(d.getAdditionalProperties(), d.getClass());
			final ObjectIdentifier oi = processObjectIdentifier(wsi, d.getName(), d.getObjid());
			String errprefix = "Object ";
			if (oi == null) {
				errprefix += count;
			} else {
				errprefix += count + ", " + oi.getIdentifierString() + ",";
			}
			if (d.getData() == null) {
				throw new IllegalArgumentException(errprefix + " has no data");
			}
			final TypeId t = KBaseIdentifierFactory.processTypeId(d.getType(), d.getTver(), errprefix);
			final Provenance p = ArgUtils.processProvenance(authPart.getUserName(), d.getProvenance());
			final boolean hidden = d.getHidden() != null && d.getHidden() != 0;
			if (oi == null) {
				woc.addObject(new WorkspaceSaveObject(wsi, ArgUtils.parseUObj(d.getData()), t,
						ArgUtils.parseUObj(d.getMetadata()), p, hidden));
			} else {
				woc.addObject(new WorkspaceSaveObject(oi, ArgUtils.parseUObj(d.getData()), t,
						ArgUtils.parseUObj(d.getMetadata()), p, hidden));
			}
//				woc.addObject(new WorkspaceSaveObject(wsi, d.getData(), t,
//						d.getMetadata(), p, hidden));
//			} else {
//				woc.addObject(new WorkspaceSaveObject(oi, d.getData(), t,
//						d.getMetadata(), p, hidden));
//			}
			count++;
		}
		
		List<ObjectMetaData> meta = ws.saveObjects(authPart.getUserName(), woc); 
		returnVal = ArgUtils.objMetaToTuple(meta);
        //END save_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_objects</p>
     * <pre>
     * Get objects from the workspace.
     * </pre>
     */
    @JsonServerMethod(rpc = "Workspace.get_objects")
    public List<ObjectData> getObjects(List<ObjectIdentity> objects) throws Exception {
        List<ObjectData> returnVal = null;
        //BEGIN get_objects
		//TODO get_objects
        //END get_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_metadata</p>
     * <pre>
     * Get object metadata from the workspace.
     * </pre>
     */
    @JsonServerMethod(rpc = "Workspace.get_object_metadata")
    public List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,UObject>>> getObjectMetadata(List<ObjectIdentity> objects) throws Exception {
        List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,UObject>>> returnVal = null;
        //BEGIN get_object_metadata
		//TODO get_object_metadata
        //END get_object_metadata
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
