package us.kbase.workspace.database;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import us.kbase.workspace.Workspace;
import us.kbase.workspace.WorkspaceMetadata;
import us.kbase.workspace.database.exceptions.AuthorizationException;
import us.kbase.workspace.database.exceptions.InvalidHostException;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

public class MongoDatabase implements Database {
	
	private final DB workspace;
	
	public MongoDatabase(String host, String database)
			throws UnknownHostException, IOException,
			InvalidHostException {
		workspace = getDB(host, database);
		validateDB();
	}
	
	public MongoDatabase(String host, String database, String user,
			String password) throws UnknownHostException, IOException,
			AuthorizationException, InvalidHostException {
		workspace = getDB(host, database);
		try {
			workspace.authenticate(user, password.toCharArray());
		} catch (MongoException.Network men) {
			throw (IOException)men.getCause();
		}
		try {
			workspace.getCollectionNames();
		} catch (MongoException me) {
			throw new AuthorizationException("Not authorized for database " +
					database, me);
		}
		validateDB();
	}
	
	private DB getDB(String host, String database) throws UnknownHostException, 
			InvalidHostException {
		//Don't print to stderr you idiots
		Logger.getLogger("com.mongodb").setLevel(Level.OFF);
		MongoClient m = null;
		try {
			m = new MongoClient(host);
		} catch (NumberFormatException nfe) {
			throw new InvalidHostException(host + " is not a valid mongodb host");
		}
		return m.getDB(database);
	}
	
	private void validateDB() {
		System.out.println(workspace.getCollectionNames());
	}

	@Override
	public WorkspaceMetadata createWorkspace(Workspace ws) {
		// TODO Auto-generated method stub
		return null;
	}

}
