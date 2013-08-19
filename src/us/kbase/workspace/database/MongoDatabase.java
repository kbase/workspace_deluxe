package us.kbase.workspace.database;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.MarshallingException;

import us.kbase.workspace.Workspace;
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
	private final BlobStore blob;
	
	private static final String SETTINGS = "settings";
	
	public MongoDatabase(String host, String database, String backendSecret)
			throws UnknownHostException, IOException,
			InvalidHostException, WorkspaceDBException {
		workspace = getDB(host, database);
		try {
			workspace.getCollectionNames();
		} catch (MongoException.Network men) {
			throw (IOException)men.getCause();
		}
		wsjongo = new Jongo(workspace);
		blob = setupDB(backendSecret);
		System.out.println(blob); //TODO Del
	}
	
	public MongoDatabase(String host, String database, String backendSecret,
			String user, String password) throws UnknownHostException,
			IOException, DBAuthorizationException, InvalidHostException,
			WorkspaceDBException {
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
		blob = setupDB(backendSecret);
		System.out.println(blob); //TODO Del
	}
	
	private DB getDB(String host, String database) throws UnknownHostException, 
			InvalidHostException {
		//Don't print to stderr
		Logger.getLogger("com.mongodb").setLevel(Level.OFF);
		MongoClient m = null;
		try {
			m = new MongoClient(host);
		} catch (NumberFormatException nfe) {
			throw new InvalidHostException(host + " is not a valid mongodb host");
		}
		return m.getDB(database);
	}
	
	private BlobStore setupDB(String backendSecret) throws WorkspaceDBException {
		if(!workspace.collectionExists(SETTINGS)) {
			throw new UninitializedWorkspaceDBException(
					"No settings collection exists");
		}
		MongoCollection settings = wsjongo.getCollection(SETTINGS);
		if(settings.count() != 1) {
			throw new CorruptWorkspaceDBException(
					"More than one settings document exists");
		}
		Settings wsSettings = null;
		try {
			wsSettings = settings.findOne().as(Settings.class);
		} catch (MarshallingException me) {
			Throwable ex = me.getCause();
			if(ex == null) {
				throw new CorruptWorkspaceDBException(
						"Unable to unmarshal settings document", me);
			}
			ex = ex.getCause();
			//I have no idea why I checked for a CWDBE here
			if(ex == null || !(ex instanceof CorruptWorkspaceDBException)) {
				throw new CorruptWorkspaceDBException(
						"Unable to unmarshal settings document", me);
			}
			throw (CorruptWorkspaceDBException) ex;
		}
		if(wsSettings.isGridFSBackend()) {
			BlobStore bs = new GridFSBackend(workspace);
			//TODO set up and return gridFS backend
			return bs;
		}
		if(wsSettings.isShockBackend()) {
			URL shockurl = null;
			try {
				shockurl = new URL(wsSettings.getShockUrl());
			} catch (MalformedURLException mue) {
				throw new CorruptWorkspaceDBException(
						"Settings has bad shock url: " + 
						wsSettings.getShockUrl(), mue);
			}
			BlobStore bs = new ShockBackend(shockurl,
					wsSettings.getShockUser(), backendSecret); 
			//TODO set up and return shock backend
			//TODO if shock, check a few random nodes to make sure they match
			//the internal representation, die otherwise
			return bs;
		}
		throw new RuntimeException("Something's real broke y'all");
	}

	@Override
	public Workspace createWorkspace(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Workspace createWorkspace(String name, String description) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Workspace createWorkspace(String name, boolean globalread) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Workspace createWorkspace(String name, boolean globalread,
			String description) {
		// TODO Auto-generated method stub
		return null;
	}

}
