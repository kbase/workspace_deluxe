package us.kbase.workspace;

import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple7;
import us.kbase.auth.AuthToken;

//BEGIN_HEADER
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.database.mongo.MongoDatabase;
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
	static {
		PERM_TO_API.put(Permission.NONE, "n");
		PERM_TO_API.put(Permission.READ, "r");
		PERM_TO_API.put(Permission.WRITE, "w");
		PERM_TO_API.put(Permission.ADMIN, "a");
		PERM_TO_API.put(false, "n"); // for globalread
		PERM_TO_API.put(true, "r"); // for globalread
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
		if (!params.getGlobalread().equals("r") && !params.getGlobalread().equals("n")) {
			throw new IllegalArgumentException("globalread must be r or n");
		}
		WorkspaceMetaData meta = ws.createWorkspace(authPart.getUserName(), params.getWorkspace(),
				params.getGlobalread().equals("r"), params.getDescription());
		System.out.println(meta);
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
		//TODO check auth
		if (!(params.getWorkspace() == null ^ params.getId() == null)) {
			throw new IllegalArgumentException("Must provide only one of workspace or id");
		}
		if (params.getId() != null) {
			returnVal = ws.getWorkspaceDescription(params.getId());
		} else {
			Matcher m = KB_WS_ID.matcher(params.getWorkspace());
			if (m.find()) {
				returnVal = ws.getWorkspaceDescription(new Integer(m.group(1)).intValue());
			} else {
				returnVal = ws.getWorkspaceDescription(params.getWorkspace());
				
			}
		}
        //END get_workspace_description
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
