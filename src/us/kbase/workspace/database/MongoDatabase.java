package us.kbase.workspace.database;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import us.kbase.workspace.Workspace;
import us.kbase.workspace.WorkspaceMetadata;
import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InvalidHostException;
import us.kbase.workspace.database.exceptions.UninitializedWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

public class MongoDatabase implements Database {
	
	private final DB workspace;
	private final Jongo wsjongo;
	
	private static final String SETTINGS = "settings";
	
	public MongoDatabase(String host, String database)
			throws UnknownHostException, IOException,
			InvalidHostException, WorkspaceDBException {
		workspace = getDB(host, database);
		try {
			workspace.getCollectionNames();
		} catch (MongoException.Network men) {
			throw (IOException)men.getCause();
		}
		wsjongo = new Jongo(workspace);
		validateDB();
	}
	
	public MongoDatabase(String host, String database, String user,
			String password) throws UnknownHostException, IOException,
			DBAuthorizationException, InvalidHostException, WorkspaceDBException {
		workspace = getDB(host, database);
		try {
			workspace.authenticate(user, password.toCharArray());
		} catch (MongoException.Network men) {
			throw (IOException)men.getCause();
		}
		try {
			workspace.getCollectionNames();
		} catch (MongoException me) {
			throw new DBAuthorizationException("Not authorized for database " +
					database, me);
		}
		wsjongo = new Jongo(workspace);
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
	
	private void validateDB() throws WorkspaceDBException {
		if(!workspace.collectionExists(SETTINGS)) {
			throw new UninitializedWorkspaceDBException(
					"No settings collection exists");
		}
		MongoCollection settings = wsjongo.getCollection(SETTINGS);
		if(settings.count() != 1) {
			throw new CorruptWorkspaceDBException(
					"More than one settings document exists");
		}
	}

	@Override
	public WorkspaceMetadata createWorkspace(Workspace ws) {
		// TODO Auto-generated method stub
		return null;
	}

}
