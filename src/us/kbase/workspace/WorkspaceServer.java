package us.kbase.workspace;

import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple7;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;

//BEGIN_HEADER
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
import us.kbase.workspace.workspaces.Permission;
import us.kbase.workspace.workspaces.WorkspaceMetaData;
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
	//required deploy parameters:
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	//required backend param:
	private static final String BACKEND_SECRET = "backend-secret"; 
	//auth params:
	private static final String USER = "mongodb-user";
	private static final String PWD = "mongodb-pwd";
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws.(\\d+)");
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
	}
	
	private final Workspaces ws;
	
	private void logger(String log) {
		//TODO when logging is released (check places that call this method)
		System.out.println(log);
	}
	private Database getDB(String host, String dbs, String secret, String user,
			String pwd) {
		try {
			if (user != null) {
				return new MongoDatabase(host, dbs, secret, user, pwd);
			} else {
				return new MongoDatabase(host, dbs, secret);
			}
		} catch (UnknownHostException uhe) {
			die("Couldn't find host " + host + ": " +
					uhe.getLocalizedMessage());
		} catch (IOException io) {
			die("Couldn't connect to host " + host + ": " +
					io.getLocalizedMessage());
		} catch (DBAuthorizationException ae) {
			die("Not authorized: " + ae.getLocalizedMessage());
		} catch (InvalidHostException ihe) {
			die(host + " is an invalid database host: "  +
					ihe.getLocalizedMessage());
		} catch (WorkspaceDBException uwde) {
			die("The workspace database is invalid: " +
					uwde.getLocalizedMessage());
		}
		return null; //shut up eclipse you bastard
	}
	
	private void die(String error) {
		System.err.println(error);
		System.err.println("Terminating server.");
		System.exit(1);
	}
	
	private String formatDate(Date d) {
		if (d == null) {
			return null;
		}
		return DATE_FORMAT.format(d);
		
	}
	
	private class WorkspaceIdentifier {
		
		public final Integer id;
		public final String workspace;
		
		public WorkspaceIdentifier(Integer id, String workspace) {
			this.id = id;
			this.workspace = workspace;
		}

		@Override
		public String toString() {
			return "WorkspaceIdentifier [id=" + id + ", workspace=" + workspace
					+ "]";
		}
	}
	
	private WorkspaceIdentifier processWorkspaceIdentifier(String workspace, Integer id) {
		if (!(workspace == null ^ id == null)) {
			throw new IllegalArgumentException("Must provide one and only one of workspace or id");
		}
		if (id != null) {
			return new WorkspaceIdentifier(id, null);
		} else {
			Matcher m = KB_WS_ID.matcher(workspace);
			if (m.find()) {
				return new WorkspaceIdentifier(new Integer(m.group(1)), null);
			} else {
				return new WorkspaceIdentifier(null, workspace);
				
			}
		}
	}
    //END_CLASS_HEADER

    public WorkspaceServer() throws Exception {
        //BEGIN_CONSTRUCTOR
		if (!config.containsKey(HOST)) {
			die("Must provide param " + HOST + " in config file");
		}
		String host = config.get(HOST);
		if (!config.containsKey(DB)) {
			die("Must provide param " + DB + " in config file");
		}
		String dbs = config.get(DB);
		if (!config.containsKey(BACKEND_SECRET)) {
			die("Must provide param " + BACKEND_SECRET + " in config file");
		}
		String secret = config.get(BACKEND_SECRET);
		if (config.containsKey(USER) ^ config.containsKey(PWD)) {
			die(String.format("Must provide both %s and %s ",
					USER, PWD) + "params in config file if authentication " + 
					"is to be used");
		}
		String user = config.get(USER);
		String pwd = config.get(PWD);
		String params = "";
		for (String s: Arrays.asList(HOST, DB, USER)) {
			if (config.containsKey(s)) {
				params += s + "=" + config.get(s) + "\n";
			}
		}
		params += BACKEND_SECRET + "=[redacted for your safety and comfort]\n";
		if (pwd != null) {
			params += PWD + "=[redacted for your safety and comfort]\n";
		}
		System.out.println("Using connection parameters:\n" + params);
		Database db = getDB(host, dbs, secret, user, pwd);
		System.out.println(String.format("Initialized %s backend", db.getBackendType()));
		ws = new Workspaces(db);
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   Original type "create_workspace_params" (see {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified timestamp deleted - date when the workspace was last deleted or null permission user_permission - permissions for the authenticated user of this workspace permission globalread - whether this workspace is globally readable.)
     */
    @JsonServerMethod(rpc = "Workspace.create_workspace")
    public Tuple7<Integer, String, String, String, String, String, String> createWorkspace(CreateWorkspaceParams params, AuthToken authPart) throws Exception {
        Tuple7<Integer, String, String, String, String, String, String> returnVal = null;
        //BEGIN create_workspace
		if (!params.getGlobalread().equals(PERM_READ) && !params.getGlobalread().equals(PERM_NONE)) {
			throw new IllegalArgumentException(String.format(
					"globalread must be %s or %s", PERM_NONE, PERM_READ));
		}
		WorkspaceMetaData meta = ws.createWorkspace(authPart.getUserName(), params.getWorkspace(),
				params.getGlobalread().equals(PERM_READ), params.getDescription());
		returnVal = new Tuple7<Integer, String, String, String, String, String,
				String>().withE1(meta.getId()).withE2(meta.getName())
				.withE3(meta.getOwner()).withE4(formatDate(meta.getModDate()))
				.withE5(formatDate(meta.getDeletedDate()))
				.withE6(PERM_TO_API.get(meta.getUserPermission())) 
				.withE7(PERM_TO_API.get(meta.isGloballyReadable()));
        //END create_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   params   Original type "get_workspace_description_params" (see {@link us.kbase.workspace.GetWorkspaceDescriptionParams GetWorkspaceDescriptionParams} for details)
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_description", authOptional=true)
    public String getWorkspaceDescription(GetWorkspaceDescriptionParams params, AuthToken authPart) throws Exception {
        String returnVal = null;
        //BEGIN get_workspace_description
		//TODO check auth, must be globally readable or user must have read perms
		WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		if (wsi.id != null) {
			returnVal = ws.getWorkspaceDescription(wsi.id);
		} else {
			returnVal = ws.getWorkspaceDescription(wsi.workspace);
		}
        //END get_workspace_description
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   Original type "set_permissions_params" (see {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams} for details)
     */
    @JsonServerMethod(rpc = "Workspace.set_permissions")
    public void setPermissions(SetPermissionsParams params, AuthToken authPart) throws Exception {
        //BEGIN set_permissions
    	//TODO make some methods out of this
		//TODO make wsi part of the api, just pass that in
		WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		Map<String, Permission> input = new HashMap<String, Permission>();
		for (Userperm up: params.getPermissions()) {
			if (up.getUser() == null) {
				throw new IllegalArgumentException(
						"The user parameter of a userperm object cannot be missing or null");
			}
			if (up.getPerm() == null) {
				throw new IllegalArgumentException(
						"The perm parameter of a userperm object cannot be missing or null");
			}
			if (!API_TO_PERM.containsKey(up.getPerm())) {
				throw new IllegalArgumentException(
						"Illegal permissions character: " + up.getPerm());
			}
			input.put(up.getUser(), API_TO_PERM.get(up.getPerm()));
		}
		Map<String, Boolean> userok = AuthService.isValidUserName(
				new ArrayList<String>(input.keySet()), authPart);
		for (String user: userok.keySet()) {
			if (!userok.get(user)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", user));
			}
		}
		//TODO verify user is owner or has admin perms.
		
		//END set_permissions
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new WorkspaceServer().startupServer(Integer.parseInt(args[0]));
    }
}
