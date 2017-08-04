package us.kbase.workspace.modules;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;

import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.mongo.exceptions.MongoAuthException;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.listener.ListenerInitializationException;
import us.kbase.workspace.listener.WorkspaceEventListener;
import us.kbase.workspace.listener.WorkspaceEventListenerFactory;

/** A prototype event handler that emits workspace events in a format understood by the KBase
 * RESKE service. In production the workspace handler should feed into a message queue with a
 * generic format and then other services should read from that queue.
 * @author gaprice@lbl.gov
 *
 */
public class RESKEPrototypeEventHandlerFactory implements WorkspaceEventListenerFactory {

	//TODO RESKE JAVADOC
	//TODO RESKE TEST
	
	@Override
	public WorkspaceEventListener configure(final Map<String, String> cfg)
			throws ListenerInitializationException {
		//TODO RESKE may need more opts for sharding
		final String mongoHost = cfg.get("mongohost");
		final String mongoDatabase = cfg.get("mongodatabase");
		String mongoUser = cfg.get("mongouser");
		if (mongoUser == null || mongoUser.trim().isEmpty()) {
			mongoUser = null;
		}
		final String mongoPwd = cfg.get("reske.mongopwd");
		LoggerFactory.getLogger(getClass()).info("Starting RESKE Prototype event handler. " +
				"mongohost={} mongodatabase={} mongouser={}", mongoHost, mongoDatabase, mongoUser);
		return new RESKEPrototypeEventHandler(mongoHost, mongoDatabase, mongoUser, mongoPwd);
	}
	
	public class RESKEPrototypeEventHandler implements WorkspaceEventListener {
		
		private static final String DATA_SOURCE = "WS";
		private static final String NEW_OBJECT = "NEW_VERSION";
		
		// this might need to be configurable
		private static final String COLLECTION = "ObjectStatusEvents";
		
		private final DB db;

		public RESKEPrototypeEventHandler(
				final String mongoHost,
				final String mongoDatabase,
				String mongoUser,
				final String mongoPwd)
				throws ListenerInitializationException {
			//TODO RESKE check args
			if (mongoUser == null || mongoUser.trim().isEmpty()) {
				mongoUser = null;
			}
			try {
				if (mongoUser == null) {
					db = GetMongoDB.getDB(mongoHost, mongoDatabase, 0, 10);
				} else {
					db = GetMongoDB.getDB(mongoHost, mongoDatabase, mongoUser, mongoPwd, 0, 10);
				}
			} catch (InterruptedException ie) {
				throw new ListenerInitializationException(
						"Connection to MongoDB was interrupted. This should never "
								+ "happen and indicates a programming problem. Error: " +
								ie.getLocalizedMessage(), ie);
			} catch (UnknownHostException uhe) {
				throw new ListenerInitializationException("Couldn't find mongo host "
						+ mongoHost + ": " + uhe.getLocalizedMessage(), uhe);
			} catch (IOException | MongoTimeoutException e) {
				throw new ListenerInitializationException("Couldn't connect to mongo host " 
						+ mongoHost + ": " + e.getLocalizedMessage(), e);
			} catch (MongoException e) {
				throw new ListenerInitializationException(
						"There was an error connecting to the mongo database: " +
								e.getLocalizedMessage());
			} catch (MongoAuthException ae) {
				throw new ListenerInitializationException("Not authorized for mongo database "
						+ mongoHost + ": " + ae.getLocalizedMessage(), ae);
			} catch (InvalidHostException ihe) {
				throw new ListenerInitializationException(mongoHost +
						" is an invalid mongo database host: "  +
						ihe.getLocalizedMessage(), ihe);
			}
		}

		@Override
		public void createWorkspace(long id) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void cloneWorkspace(long id) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void setWorkspaceMetadata(long id) {
			// no action
		}

		@Override
		public void lockWorkspace(long id) {
			// no action
		}

		@Override
		public void renameWorkspace(long id, String newname) {
			// TODO RESKE Auto-generated method stub
			// not sure if there's anything to do here
		}

		@Override
		public void setGlobalPermission(long id, Permission permission) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void setPermissions(long id, Permission permission, List<WorkspaceUser> users) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void setWorkspaceDescription(long id) {
			// no action
			
		}

		@Override
		public void setWorkspaceOwner(long id, WorkspaceUser newUser, Optional<String> newName) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void setWorkspaceDeleted(long id, boolean delete) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void revertObject(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			newVersionEvent(workspaceId, objectId, version, type, isPublic);
			
		}

		@Override
		public void setObjectDeleted(long workspaceId, long objectId, boolean delete) {
			// TODO RESKE Auto-generated method stub
			
		}

		@Override
		public void copyObject(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			newVersionEvent(workspaceId, objectId, version, type, isPublic);
		}

		@Override
		public void copyObject(
				long workspaceId,
				long objectId,
				int latestVersion,
				boolean isPublic) {
			// TODO RESKE Auto-generated method stub
		}
		
		@Override
		public void saveObject(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			newVersionEvent(workspaceId, objectId, version, type, isPublic);
		}

		private void newVersionEvent(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			if (!wsidOK(workspaceId)) {
				return;
			}
			
			final DBObject dobj = new BasicDBObject();
			dobj.put("storageCode", DATA_SOURCE);
			dobj.put("accessGroupId", (int) workspaceId);
			dobj.put("accessGroupObjectId", "" + objectId);
			dobj.put("version", version);
			dobj.put("timestamp", System.currentTimeMillis());
			dobj.put("eventType", NEW_OBJECT);
			dobj.put("storageObjectType", type.split("-")[0]);
			dobj.put("isGlobalAccessed", isPublic);
			dobj.put("indexed", false);
			dobj.put("processed", false);
			try {
				db.getCollection(COLLECTION).insert(dobj);
			} catch (MongoException me) {
				LoggerFactory.getLogger(getClass()).error(String.format(
						"RESKE save %s/%s/%s: Failed to connect to MongoDB",
						workspaceId, objectId, version), me);
			}
		}
		
		private boolean wsidOK(final long workspaceId) {
			if (workspaceId > Integer.MAX_VALUE) {
				LoggerFactory.getLogger(getClass()).error(
						"Workspace id {} is out of int range. Cannot send data to RESKE",
						workspaceId);
				return false;
			}
			return true;
		}
	}
}
