package us.kbase.workspace;

import java.util.List;
import java.util.Map;
import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple10;
import us.kbase.Tuple6;
import us.kbase.auth.AuthToken;

//BEGIN_HEADER
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.auth.AuthService;
import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.kbase.KBWorkspaceIDFactory;
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
	
	private static final String TYPE_SEP = "\\."; //regex
	private static final String VER_SEP = "\\."; //regex
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final Map<Object, String> PERM_TO_API = new HashMap<Object, String>();
	private static final Map<String, Permission> API_TO_PERM = new HashMap<String, Permission>();
	private static final String PERM_NONE = "n";
	private static final String PERM_READ = "r";
	private static final String PERM_WRITE = "w";
	private static final String PERM_ADMIN = "a";
	static {
		API_TO_PERM.put(PERM_NONE, Permission.NONE);
		API_TO_PERM.put(PERM_READ, Permission.READ);
		API_TO_PERM.put(PERM_WRITE, Permission.WRITE);
		API_TO_PERM.put(PERM_ADMIN, Permission.ADMIN);
		for (String p: API_TO_PERM.keySet()) {
			PERM_TO_API.put(API_TO_PERM.get(p), p);
		}
		PERM_TO_API.put(false, PERM_NONE); // for globalread
		PERM_TO_API.put(true, PERM_READ); // for globalread
		PERM_TO_API.put(Permission.OWNER, PERM_ADMIN);
	}
	
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
	
	private String formatDate(final Date d) {
		if (d == null) {
			return null;
		}
		return DATE_FORMAT.format(d);
	}
	
	private WorkspaceIdentifier processWorkspaceIdentifier(final WorkspaceIdentity wsi) {
		return processWorkspaceIdentifier(wsi.getWorkspace(), wsi.getId());
	}
	
	private WorkspaceIdentifier processWorkspaceIdentifier(final String workspace, final Integer id) {
		if (!(workspace == null ^ id == null)) {
			throw new IllegalArgumentException("Must provide one and only one of workspace or id");
		}
		if (id != null) {
			return KBWorkspaceIDFactory.create(id);
		}
		return KBWorkspaceIDFactory.create(workspace);
	}
	
	private ObjectIdentifier processObjectIdentifier(final WorkspaceIdentifier wsi,
			final String name, final Integer id) {
		if (name == null && id == null) {
			return null;
		}
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of an object name or id: %s/%s",
					name, id));
		}
		if (name != null) {
			return new ObjectIdentifier(wsi, name);
		}
		return new ObjectIdentifier(wsi, id);
	}
	
	private TypeId processTypeId(final String type, final String ver,
			final String errprefix) {
		if (type == null) {
			throw new IllegalArgumentException(errprefix + " has no type");
		}
		final String[] t = type.split(TYPE_SEP);
		if (t.length != 2) {
			throw new IllegalArgumentException(errprefix + String.format(
					" type %s could not be split into a module and name",
					type));
		}
		final WorkspaceType wt = new WorkspaceType(t[0], t[1]);
		if (ver == null) {
			return new TypeId(wt);
		}
		final String[] v = ver.split(VER_SEP);
		if (v.length == 1) {
			try {
				return new TypeId(wt, Integer.parseInt(v[0]));
			} catch (NumberFormatException ne) {
				throwTypeVerException(errprefix, ver);
			}
		}
		if (v.length == 2) {
			try {
				return new TypeId(wt, Integer.parseInt(v[0]),
						Integer.parseInt(v[1]));
			} catch (NumberFormatException ne) {
				throwTypeVerException(errprefix, ver);
			}
		}
		throwTypeVerException(errprefix, ver);
		return null; //shut up java
	}
	
	private void throwTypeVerException(final String errprefix, final String ver) {
		throw new IllegalArgumentException(errprefix + String.format(
				" type version string %s could not be parsed to a version",
				ver));
	}
	
	private Provenance processProvenance(String user,
			List<ProvenanceAction> actions) {
		
		Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			Provenance.ProvenanceAction pa = new Provenance.ProvenanceAction();
			if (a.getService() != null) {
				pa = pa.withServiceName(a.getService());
			}
			//TODO remainder of provenance actions
		}
		
		return p;
	}
	
	private Tuple6<Integer, String, String, String, String, String> wsMetaToTuple (
			WorkspaceMetaData meta) {
		return new Tuple6<Integer, String, String, String, String, String>()
				.withE1(meta.getId()).withE2(meta.getName())
				.withE3(meta.getOwner()).withE4(formatDate(meta.getModDate()))
				.withE5(PERM_TO_API.get(meta.getUserPermission())) 
				.withE6(PERM_TO_API.get(meta.isGloballyReadable()));
	}
	
	private List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, Object>>>
			objMetaToTuple (List<ObjectMetaData> meta) {
		
		//oh the humanity
		final List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, Object>>> ret = 
			new ArrayList<Tuple10<Integer, String, String, String, Integer,
			String, Integer, String, Integer, Map<String, Object>>>();
		
		for (ObjectMetaData m: meta) {
			ret.add(new Tuple10<Integer, String, String, String, Integer,
					String, Integer, String, Integer, Map<String, Object>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(formatDate(m.getCreatedDate()))
					.withE5(m.getVersion())
					.withE6(m.getCreator())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize())
					.withE10(m.getUserMetaData()));
		}
		return ret;
	}
	
	private void checkAddlArgs(Map<String, Object> addlargs,
			@SuppressWarnings("rawtypes") Class clazz) {
		if (addlargs.isEmpty()) {
			return;
		}
		throw new IllegalArgumentException(String.format(
				"Unexpected arguments in %s: %s",
				clazz.getName().substring(clazz.getName().lastIndexOf(".") + 1),
				StringUtils.join(addlargs.keySet(), " ")));
	}
	
	private String getUserName(AuthToken token) {
		if (token == null) {
			return null;
		}
		return token.getUserName();
	}
	
//	private Map<String, Object> removeUObj(Map<String, UObject> map) {
//		Map<String, Object> ret = new HashMap<String, Object>();
//		for (String s: map.keySet()) {
//			ret.put(s, map.get(s).getUserObject());
//		}
//		return ret;
//	}
	
//	private Map<String, UObject> addUObj(Map<String, Object> map) {
//		Map<String, UObject> ret = new HashMap<String, UObject>();
//		for (String s: map.keySet()) {
//			ret.put(s, new UObject(map.get(s)));
//		}
//		return ret;
//	}
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
			p = API_TO_PERM.get(params.getGlobalread());
		}
		final WorkspaceMetaData meta = ws.createWorkspace(authPart.getUserName(), params.getWorkspace(),
				p.equals(Permission.READ), params.getDescription());
		returnVal = wsMetaToTuple(meta);
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
		final WorkspaceMetaData meta = ws.getWorkspaceMetaData(getUserName(authPart), wksp);
		returnVal = wsMetaToTuple(meta);
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
		returnVal = ws.getWorkspaceDescription(getUserName(authPart), wksp);
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
		if (API_TO_PERM.get(params.getNewPermission()) == null) {
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
				API_TO_PERM.get(params.getNewPermission()));
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
			returnVal.put(acl, PERM_TO_API.get(acls.get(acl)));
		}
        //END get_permissions
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace
     * </pre>
     * @param   params   Original type "SaveObjectsParams" (see {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams} for details)
     */
    @JsonServerMethod(rpc = "Workspace.save_objects")
    public List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,Object>>> saveObjects(SaveObjectsParams params, AuthToken authPart) throws Exception {
        List<Tuple10<Integer, String, String, String, Integer, String, Integer, String, Integer, Map<String,Object>>> returnVal = null;
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
			final TypeId t = processTypeId(d.getType(), d.getTver(), errprefix);
			final Provenance p = processProvenance(authPart.getUserName(), d.getProvenance());
			final boolean hidden = d.getHidden() != null && d.getHidden() != 0;
			if (oi == null) {
//				woc.addObject(new WorkspaceSaveObject(wsi, removeUObj(d.getData()), t,
//						removeUObj(d.getMetadata()), p, d.getHidden() != 0));
//			} else {
//				woc.addObject(new WorkspaceSaveObject(oi, removeUObj(d.getData()), t,
//						removeUObj(d.getMetadata()), p, d.getHidden() != 0));
//			}
				woc.addObject(new WorkspaceSaveObject(wsi, d.getData(), t,
						d.getMetadata(), p, hidden));
			} else {
				woc.addObject(new WorkspaceSaveObject(oi, d.getData(), t,
						d.getMetadata(), p, hidden));
			}
			count++;
		}
		
		//TODO error if additional args
		List<ObjectMetaData> meta = ws.saveObjects(authPart.getUserName(), woc); 
		returnVal = objMetaToTuple(meta);
        //END save_objects
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
