package us.kbase.workspace.modules;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Instant;
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
import us.kbase.workspace.database.ObjectInformation;
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
		final String mongoPwd = cfg.get("mongopwd");
		LoggerFactory.getLogger(getClass()).info("Starting RESKE Prototype event handler. " +
				"mongohost={} mongodatabase={} mongouser={}", mongoHost, mongoDatabase, mongoUser);
		return new RESKEPrototypeEventHandler(mongoHost, mongoDatabase, mongoUser, mongoPwd);
	}
	
	public class RESKEPrototypeEventHandler implements WorkspaceEventListener {
		
		private static final String DATA_SOURCE = "WS";
		private static final String NEW_OBJECT_VER = "NEW_VERSION";
		private static final String NEW_OBJECT = "NEW_ALL_VERSIONS";
		private static final String CLONED_WORKSPACE = "COPY_ACCESS_GROUP";
		private static final String RENAME_OBJECT = "RENAME_ALL_VERSIONS";
		private static final String DELETE_OBJECT = "DELETE_ALL_VERSIONS";
		private static final String UNDELETE_OBJECT = "UNDELETE_ALL_VERSIONS";
		private static final String DELETE_WS = "DELETE_ACCESS_GROUP";
		private static final String SET_GLOBAL_READ = "PUBLISH_ACCESS_GROUP";
		private static final String REMOVE_GLOBAL_READ = "UNPUBLISH_ACCESS_GROUP";
		
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
			// no action
		}

		@Override
		public void cloneWorkspace(final long id, final boolean isPublic) {
			//TODO TIMESTAMP
			newWorkspaceEvent(id, CLONED_WORKSPACE, isPublic, null);
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
			// no action
		}

		@Override
		public void setGlobalPermission(long id, Permission permission) {
			//TODO TIMESTAMP
			newWorkspaceEvent(id, Permission.READ.equals(permission) ?
					SET_GLOBAL_READ : REMOVE_GLOBAL_READ, null, null);
			
		}

		@Override
		public void setPermissions(long id, Permission permission, List<WorkspaceUser> users) {
			// no action
		}

		@Override
		public void setWorkspaceDescription(long id) {
			// no action
			
		}

		@Override
		public void setWorkspaceOwner(long id, WorkspaceUser newUser, Optional<String> newName) {
			// no action
		}

		@Override
		public void setWorkspaceDeleted(
				final long id,
				final boolean delete,
				final long maxObjectID) {
			//TODO TIMESTAMP
			if (delete) {
				newEvent(id, maxObjectID, null, null, null, DELETE_WS, null, null);
			} else {
				LoggerFactory.getLogger(getClass()).info(
						"Workspace {} was undeleted. Workspace undeletion events are not " +
								"supported by RESKE", id);
			}
			
		}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName) {
			//TODO TIMESTAMP
			newEvent(workspaceId, objectId, null, newName, null, RENAME_OBJECT, null, null);
		}

		@Override
		public void revertObject(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			//TODO TIMESTAMP
			newVersionEvent(workspaceId, objectId, version, type, isPublic, null);
			
		}

		@Override
		public void setObjectDeleted(long workspaceId, long objectId, boolean delete) {
			//TODO TIMESTAMP
			newEvent(workspaceId, objectId, null, null, null,
					delete ? DELETE_OBJECT : UNDELETE_OBJECT, null, null);
		}

		@Override
		public void copyObject(
				final long workspaceId,
				final long objectId,
				final int version,
				final String type,
				final boolean isPublic) {
			//TODO TIMESTAMP
			newVersionEvent(workspaceId, objectId, version, type, isPublic, null);
		}

		@Override
		public void copyObject(
				long workspaceId,
				long objectId,
				int latestVersion,
				boolean isPublic) {
			//TODO TIMESTAMP
			newObjectEvent(workspaceId, objectId, isPublic, null);
		}
		
		@Override
		public void saveObject(final ObjectInformation oi, final boolean isPublic) {
			newVersionEvent(oi.getWorkspaceId(), oi.getObjectId(), oi.getVersion(),
					oi.getTypeString(), isPublic, oi.getSavedDate().toInstant());
		}

		private void newObjectEvent(
				final long workspaceId,
				final long objectId,
				final boolean isPublic,
				final Instant time) {
			newEvent(workspaceId, objectId, null, null, null, NEW_OBJECT, isPublic, time);
		}
		
		private void newVersionEvent(
				final long workspaceId,
				final long objectId,
				final Integer version,
				final String type,
				final boolean isPublic,
				final Instant time) {
			newEvent(workspaceId, objectId, version, null, type, NEW_OBJECT_VER, isPublic, time);
		}
		
		private void newWorkspaceEvent(
				final long workspaceId,
				final String eventType,
				final Boolean isPublic,
				final Instant time) {
			newEvent(workspaceId, null, null, null, null, eventType, isPublic, time);
		}
		
		private void newEvent(
				final long workspaceId,
				final Long objectId,
				final Integer version,
				final String newName,
				final String type,
				final String eventType,
				final Boolean isPublic,
				final Instant time) {
			if (!wsidOK(workspaceId)) {
				return;
			}
			
			final DBObject dobj = new BasicDBObject();
			dobj.put("storageCode", DATA_SOURCE);
			dobj.put("accessGroupId", (int) workspaceId);
			dobj.put("accessGroupObjectId", objectId == null ? null : "" + objectId);
			dobj.put("version", version);
			dobj.put("newName", newName);
			//TODO RESKE make timestamp = the event timestamp (e.g. object creation/rename)
			dobj.put("timestamp", time == null ? System.currentTimeMillis() : time.toEpochMilli());
			dobj.put("eventType", eventType);
			dobj.put("storageObjectType", type == null ? null : type.split("-")[0]);
			dobj.put("storageObjectTypeVersion", type == null ?
					null : Integer.parseInt(type.split("-")[1].split("\\.")[0]));
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
