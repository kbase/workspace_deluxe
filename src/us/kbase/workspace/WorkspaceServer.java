package us.kbase.workspace;

import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple5;
import us.kbase.auth.AuthUser;

//BEGIN_HEADER
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.MongoDatabase;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
//END_HEADER

public class WorkspaceServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
	//required deploy parameters:
	private static final String HOST = "mongodb-host";
	private static final String DB = "mongodb-database";
	//auth params:
	private static final String USER = "mongodb-user";
	private static final String PWD = "mongodb-pwd";
	
	private final Database db;
	
	private void logger(String log) {
		//TODO when logging is released (check places that call this method)
		System.out.println(log);
	}
	private Database getDB(String host, String dbs, String user, String pwd) {
		try {
			if (user != null) {
				return new MongoDatabase(host, dbs, user, pwd);
			} else {
				return new MongoDatabase(host, dbs);
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
		if (config.containsKey(USER) ^ config.containsKey(PWD)) {
			die(String.format("Must provide both %s and %s ",
					HOST, PWD) + "params in config file if authentication " + 
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
		if (pwd != null) {
			params += PWD + "=[redacted for your safety and comfort]\n";
		}
		System.out.println("Using connection parameters:\n" + params);
		db = getDB(host, dbs, user, pwd);
        //END_CONSTRUCTOR
    }

    @JsonServerMethod(rpc = "Workspace.create_workspace")
    public Tuple5<String, String, String, String, String> createWorkspace(CreateWorkspaceParams params, AuthUser authPart) throws Exception {
        Tuple5<String, String, String, String, String> ret = null;
        //BEGIN create_workspace
        //END create_workspace
        return ret;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new WorkspaceServer().startupServer(Integer.parseInt(args[0]));
    }
}
