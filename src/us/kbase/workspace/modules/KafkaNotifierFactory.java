package us.kbase.workspace.modules;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;

import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.kbase.KBasePermissions;
import us.kbase.workspace.listener.ListenerInitializationException;
import us.kbase.workspace.listener.WorkspaceEventListener;
import us.kbase.workspace.listener.WorkspaceEventListenerFactory;

/** A workspace listener that sends workspace events to Kafka as JSON strings:
 * 
 * <pre>
 * {
 *   "user": &lt;the user that triggered the event.
 *        May be null if the user is an administrator&gt;,
 *   "wsid": &lt;the workspace id of the workspace involved in the event&gt;,
 *   "objid": &lt;the object id of the object involved in the event. May be null&gt;
 *   "ver": &lt;the version of the object involved in the event. May be null&gt;
 *   "time": &lt;the time the event took place in epoch milliseconds&gt;
 *   "evtype": &lt;the event type. See the class constants for types&gt;
 *   "objtype": &lt;the type of the object involved in the event. May be null&gt;
 *   "perm": &lt;the permission set for one or more users. May be null&gt;
 *   "permusers": &lt;the list of users for whom permissions were altered. May be empty&gt;
 * } 
 * </pre>
 * 
 * Null values are present for events where providing the value doesn't make sense; for example
 * {@link WorkspaceEventListener#copyObject(WorkspaceUser, long, long, int, Instant, boolean)}
 * will not provide a version, object type, permission, or user list.
 * 
 * The listener requires two configuration arguments:
 * topic - the topic to which the listener will submit events. The listener requires the topic
 * name to consist of ASCII alphanumeric values and the hyphen to avoid Kafka issues around
 * ambiguity between period and underscore values. 
 * bootstrap.servers - the Kafka bootstrap servers.
 * 
 * The listener is configured to require a full write to all the replicates before a call
 * to Kafka returns, and if a write fails, an exception is thrown in the thread that called
 * the listener.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class KafkaNotifierFactory implements WorkspaceEventListenerFactory {

		// TODO TEST integration tests w/ just client & also full group server
		
		/* This implementation does things that slow down the send operation but improve
		 * reliability and user messaging:
		 * 1) Require full write to replicates before Kafka returns
		 * 2) Wait for the return and check it worked. If not, throw an exception *in the calling
		 * thread*. Thus the user is notified if something goes wrong.
		 * 
		 * If this turns out to be a bad plan, we may need to relax those requirements.
		 * 
		 * To improve reliability further, we'd need persistent storage of unsent messages.
		 */
		
		private static final String KAFKA = "Kafka";
		private static final String KCFG_BOOSTRAP_SERVERS = "bootstrap.servers";
		// may want to split events into different topics
		private static final String TOPIC = "topic";
		private static final String KAFKA_WS_TOPIC = KAFKA + " " + TOPIC;
		
		/** The event type sent by the
		 * {@link WorkspaceEventListener#saveObject(ObjectInformation, boolean)},
		 * {@link WorkspaceEventListener#copyObject(ObjectInformation, boolean)}, and
		 * {@link WorkspaceEventListener#revertObject(ObjectInformation, boolean)}
		 * methods.
		 */
		public static final String NEW_VERSION = "NEW_VERSION";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#copyObject(WorkspaceUser, long, long, int, Instant, boolean)}
		 * method.
		 */
		public static final String COPY_OBJECT = "COPY_OBJECT";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#cloneWorkspace(WorkspaceUser, long, boolean, Instant)}
		 * method.
		 */
		public static final String CLONE_WORKSPACE = "CLONE_WORKSPACE";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#renameObject(WorkspaceUser, long, long, String, Instant)}
		 * method.
		 */
		public static final String RENAME_OBJECT = "RENAME_OBJECT";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#setObjectDeleted(WorkspaceUser, long, long, boolean, Instant)}
		 * method.
		 */
		public static final String OBJECT_DELETE_STATE_CHANGE = "OBJECT_DELETE_STATE_CHANGE";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#setWorkspaceDeleted(WorkspaceUser, long, boolean, long, Instant)}
		 * method.
		 */
		public static final String WORKSPACE_DELETE_STATE_CHANGE =
				"WORKSPACE_DELETE_STATE_CHANGE";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#setPermissions(WorkspaceUser, long, Permission, List, Instant)}
		 * method.
		 */
		public static final String SET_PERMISSION = "SET_PERMISSION";
		/** The event type sent by the
		 * {@link WorkspaceEventListener#setGlobalPermission(WorkspaceUser, long, Permission, Instant)}
		 * method.
		 */
		public static final String SET_GLOBAL_PERMISSION = "SET_GLOBAL_PERMISSION";
		
		
		// https://stackoverflow.com/questions/37062904/what-are-apache-kafka-topic-name-limitations
		// Don't include . and _ because of
		// https://github.com/mcollina/ascoltatori/issues/165#issuecomment-267314016
		private final static Pattern INVALID_TOPIC_CHARS = Pattern.compile("[^a-zA-Z0-9-]+");
	
	@Override
	public WorkspaceEventListener configure(final Map<String, String> cfg)
			throws ListenerInitializationException {
		requireNonNull(cfg, "cfg");
		//TODO KAFKA support other config options (ssl etc). Unfortunately will have to parse each key individually as different types are required.
		final Map<String, Object> kcfg = new HashMap<>();
		final String topic = (String) cfg.get(TOPIC);
		final String bootstrapServers = cfg.get(KCFG_BOOSTRAP_SERVERS);
		checkString(bootstrapServers, KAFKA + " " + KCFG_BOOSTRAP_SERVERS);
		// maybe make this config accessible in the factory so it can be tested in integration tests
		kcfg.put(KCFG_BOOSTRAP_SERVERS, bootstrapServers);
		kcfg.put("acks", "all");
		kcfg.put("enable.idempotence", true);
		kcfg.put("delivery.timeout.ms", 30000);
		return new KafkaNotifier(
				topic,
				bootstrapServers,
				new KafkaProducer<>(kcfg, new StringSerializer(), new MapSerializer()));
	}

	/** A Kafka JSON serializer for arbitrary maps. Requires no configuration. The topic
	 * argument in the {@link #serialize(String, Map) method is ignored.
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class MapSerializer implements Serializer<Map<String, Object>> {

		private static final ObjectMapper MAPPER = new ObjectMapper();
		
		/** Create the serializer. */
		public MapSerializer() {}
		
		@Override
		public void close() {
			// nothing to do;
		}

		@Override
		public void configure(Map<String, ?> arg0, boolean arg1) {
			// nothing to do
		}

		@Override
		public byte[] serialize(final String topic, final Map<String, Object> data) {
			try {
				return MAPPER.writeValueAsBytes(requireNonNull(data, "data"));
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Unserializable data sent to Kafka: " + e.getMessage(),
						e);
			}
		}
	}
	
	private static class KafkaNotifier implements WorkspaceEventListener {
		
		private final String topic;
		private final KafkaProducer<String, Map<String, Object>> client;
		
		// constructor is here to allow for unit tests
		private KafkaNotifier(
				final String topic,
				final String bootstrapServers,
				final KafkaProducer<String, Map<String, Object>> client)
				throws ListenerInitializationException {
			this.topic = checkString(topic, KAFKA_WS_TOPIC, 249);
			final Matcher m = INVALID_TOPIC_CHARS.matcher(this.topic);
			if (m.find()) {
				throw new ListenerInitializationException(String.format(
						"Illegal character in %s %s: %s",
						KAFKA_WS_TOPIC, this.topic, m.group()));
			}
			this.client = requireNonNull(client, "client");
			try {
				client.partitionsFor(this.topic); // check kafka is up
			} catch (KafkaException e) {
				// TODO KAFKA CODE this blocks forever, needs 2.2.0 for a fix.
				// https://issues.apache.org/jira/browse/KAFKA-5503
				client.close(0, TimeUnit.MILLISECONDS);
				// might want a notifier exception here
				throw new ListenerInitializationException("Could not reach Kafka instance at " +
						bootstrapServers);
			}
		}
		
		private void post(final Map<String, Object> message) {
			final Future<RecordMetadata> res = client.send(new ProducerRecord<>(topic, message));
			try {
				res.get(35000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException | TimeoutException e) {
				//TODO KAFKA ERR include timeout in error message
				throw new RuntimeException("Failed sending notification to Kafka: " +
						e.getMessage(), e);
			} catch (ExecutionException e) {
				throw new RuntimeException("Failed sending notification to Kafka: " +
						e.getCause().getMessage(), e.getCause());
			}
		}

		@Override
		public void createWorkspace(WorkspaceUser user, long id, Instant time) {
			// no action
		}

		@Override
		public void cloneWorkspace(
				final WorkspaceUser user,
				final long id,
				final boolean isPublic,
				final Instant time) {
			newEvent(user.getUser(), id, null, null, null, CLONE_WORKSPACE, time);
		}

		@Override
		public void setWorkspaceMetadata(WorkspaceUser user, long id, Instant time) {
			// no action
		}

		@Override
		public void lockWorkspace(WorkspaceUser user, long id, Instant time) {
			// no action
		}

		@Override
		public void renameWorkspace(WorkspaceUser user, long id, String newname, Instant time) {
			// no action
		}

		@Override
		public void setGlobalPermission(
				final WorkspaceUser user,
				final long id,
				final Permission permission,
				final Instant time) {
			newEvent(user.getUser(), id, null, null, null, SET_GLOBAL_PERMISSION, time);
		}

		@Override
		public void setPermissions(
				final WorkspaceUser user,
				final long id,
				final Permission permission,
				final List<WorkspaceUser> users,
				final Instant time) {
			newEvent(user == null ? null : user.getUser(), id, null, null, null, SET_PERMISSION,
					time, permission, users);
		}

		@Override
		public void setWorkspaceDescription(WorkspaceUser user, long id, Instant time) {
			// no action
		}

		@Override
		public void setWorkspaceOwner(
				WorkspaceUser user,
				long id,
				WorkspaceUser newUser,
				Optional<String> newName,
				Instant time) {
			// no action
		}

		@Override
		public void setWorkspaceDeleted(
				final WorkspaceUser user,
				final long id,
				final boolean delete,
				final long maxObjectID,
				final Instant time) {
			newEvent(user == null ? null : user.getUser(), id, null, null, null,
					WORKSPACE_DELETE_STATE_CHANGE, time);
		}

		@Override
		public void renameObject(
				final WorkspaceUser user,
				final long workspaceId,
				final long objectId,
				final String newName,
				final Instant time) {
			newEvent(user.getUser(), workspaceId, objectId, null, null, RENAME_OBJECT, time);
		}

		@Override
		public void revertObject(final ObjectInformation oi, final boolean isPublic) {
			newEvent(oi.getSavedBy().getUser(), oi.getWorkspaceId(), oi.getObjectId(),
					oi.getVersion(), oi.getTypeString(), NEW_VERSION,
					oi.getSavedDate().toInstant());
			
		}

		@Override
		public void setObjectDeleted(
				final WorkspaceUser user,
				final long workspaceId,
				final long objectId,
				final boolean delete,
				final Instant time) {
			newEvent(user.getUser(), workspaceId, objectId, null, null, OBJECT_DELETE_STATE_CHANGE,
					time);
		}

		@Override
		public void copyObject(final ObjectInformation oi, final boolean isPublic) {
			newEvent(oi.getSavedBy().getUser(), oi.getWorkspaceId(), oi.getObjectId(),
					oi.getVersion(), oi.getTypeString(), NEW_VERSION,
					oi.getSavedDate().toInstant());
		}

		@Override
		public void copyObject(
				final WorkspaceUser user,
				final long workspaceId,
				final long objectId,
				final int latestVersion,
				final Instant time,
				final boolean isPublic) {
			newEvent(user.getUser(), workspaceId, objectId, null, null, COPY_OBJECT, time);
		}

		@Override
		public void saveObject(final ObjectInformation oi, final boolean isPublic) {
			newEvent(oi.getSavedBy().getUser(), oi.getWorkspaceId(), oi.getObjectId(),
					oi.getVersion(), oi.getTypeString(), NEW_VERSION,
					oi.getSavedDate().toInstant());
		}
		
		private void newEvent(
				final String user,
				final long workspaceId,
				final Long objectId,
				final Integer version,
				final String type,
				final String eventType,
				final Instant time) {
			newEvent(user, workspaceId, objectId, version, type, eventType, time, null,
					Collections.emptyList());
		}
		
		private void newEvent(
				final String user,
				final long workspaceId,
				final Long objectId,
				final Integer version,
				final String type,
				final String eventType,
				final Instant time,
				final Permission permission,
				final List<WorkspaceUser> usersWithNewPermission) {
			
			final Map<String, Object> dobj = new HashMap<>();
			dobj.put("user", user);
			dobj.put("wsid", workspaceId);
			dobj.put("objid", objectId);
			dobj.put("ver", version);
			dobj.put("time", time.toEpochMilli());
			dobj.put("evtype", eventType);
			dobj.put("objtype", type);
			dobj.put("perm", permission == null ? null :
				KBasePermissions.translatePermission(permission));
			dobj.put("permusers", usersWithNewPermission.stream().map(u -> u.getUser())
					.collect(Collectors.toList()));
			post(dobj);
		}
		
	}
	
}
