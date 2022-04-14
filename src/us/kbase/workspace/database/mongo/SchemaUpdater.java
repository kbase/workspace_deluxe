package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_WORKSPACE_VERS;
import static us.kbase.workspace.database.mongo.CollectionNames.COL_SCHEMA_CONFIG;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.bson.Document;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBInitializationException;

/** Class to handle updating the Workspace database schema for new versions.
 *
 */
public class SchemaUpdater {
	
	/** Create the updater. */
	public SchemaUpdater() {}
	
	/** Update the schema. Creates all v2 schema workspace indexes on startup, as well as
	 * the type / checksum index from the v1 schema. All index creation is in the foreground.
	 * 
	 * WARNING: Do NOT set the complete parameter to true if other processes may be altering
	 * the database while the update occurs.
	 * 
	 * @param db the database to update.
	 * @param logger a logger to which the updater can write messages.
	 * @param complete mark the update as complete in the mongo database. Only set to true if
	 * no other connection to the database are in use while the updater runs.
	 * @param overrideVersionCheck continue with the update even if the update has been
	 * marked as complete in the database.
	 * @return the number of object versions updated.
	 * @throws SchemaUpdateException if the update fails. 
	 */
	public long update(
			final MongoDatabase db,
			final Consumer<String> logger,
			final boolean complete,
			final boolean overrideVersionCheck)
			throws SchemaUpdateException {
		final Optional<Integer> schemaVer = setUp(db, logger, overrideVersionCheck);
		if (!schemaVer.isPresent()) {
			return 0; // nothing to update, the workspace has never been started for this DB.
		}
		
		// this section of code updates from schema v1 to v2, and currently that's all the
		// updates there are. If more updates are required, this probably needs to be
		// refactored to handle all the updates or punt and require one update at a time,
		// depending on the combined update complexity.
		try {
			int processedtypes = 0;
			long processedobjs = 0;
			final MongoCollection<Document> col = db.getCollection(COL_WORKSPACE_VERS);
			// this index is expected to be available on the v1 schema, but the v2 schema
			// does not require it. If updating from v2, skip this step.
			col.createIndex(
					new Document(Fields.VER_TYPE_FULL, 1).append(Fields.VER_CHKSUM, 1));
			logger.accept("Finding types to be updated");
			final List<String> types = new LinkedList<>();
			col.distinct(  // assume not many types, < 10k
					Fields.VER_TYPE_FULL, new Document(Fields.VER_TYPE_NAME, null), String.class
			).forEach((Consumer<String>) t -> types.add(t));
			logger.accept(types.size() + " types found");
			// this could be parallelized if necessary
			for (final String t: types) {
				final AbsoluteTypeDefId type = AbsoluteTypeDefId.fromAbsoluteTypeString(t);
				final UpdateResult res = col.updateMany(
						new Document(Fields.VER_TYPE_FULL, type.getTypeString())
								.append(Fields.VER_TYPE_NAME, null),
						new Document("$set", new Document(
								Fields.VER_TYPE_NAME, type.getType().getTypeString())
								.append(Fields.VER_TYPE_MAJOR_VERSION, type.getMajorVersion())
								.append(Fields.VER_TYPE_MINOR_VERSION, type.getMinorVersion()))
				);
				processedtypes++;
				processedobjs += res.getModifiedCount();
				logger.accept(String.format(
						"Processed type #%s/%s: %s, object count: %s, cumulative: %s",
						processedtypes, types.size(), t, res.getModifiedCount(), processedobjs));
			}
			if (complete && schemaVer.get() < MongoWorkspaceDB.SCHEMA_VERSION) {
				db.getCollection(COL_SCHEMA_CONFIG).updateOne(
						new Document(Fields.SCHEMA_CONFIG_KEY, Fields.SCHEMA_CONFIG_VALUE),
						new Document("$set", new Document(
								Fields.SCHEMA_CONFIG_VERSION, MongoWorkspaceDB.SCHEMA_VERSION))
				);
			}
			return processedobjs;
		} catch (MongoException e) {
			throw new SchemaUpdateException("Failed to contact database", e);
		}
	}
	
	private static Optional<Integer> setUp(
			final MongoDatabase db,
			final Consumer<String> logger,
			final boolean overrideVersionCheck)
			throws SchemaUpdateException {
		requireNonNull(logger, "logger");
		final Optional<Integer> schemaVer;
		try {
			MongoWorkspaceDB.ensureIndexes(requireNonNull(db, "db"));
			schemaVer = MongoWorkspaceDB.checkExtantSchemaAndGetVersion(db, true, true);
		} catch (CorruptWorkspaceDBException | WorkspaceDBInitializationException |
				WorkspaceCommunicationException e) {
			throw new SchemaUpdateException("Couldn't initialize database: " +
				e.getLocalizedMessage(), e);
		}
		if (schemaVer.isPresent() &&
				!overrideVersionCheck &&
				schemaVer.get() >= MongoWorkspaceDB.SCHEMA_VERSION) {
			throw new SchemaUpdateException(String.format(
					"Current DB schema version is %s, while the version to be updated to is %s. " +
					"Update is already complete.",
					schemaVer.get(), MongoWorkspaceDB.SCHEMA_VERSION));
		}
		return schemaVer;
	}
	
	/** An exception occurring during a schema update. *
	 */
	@SuppressWarnings("serial")
	public static class SchemaUpdateException extends Exception {
		
		/** Create the exception.
		 * @param message the exception message.
		 */
		public SchemaUpdateException(final String message) {
			super(message);
		}
		
		/** Create the exception with a cause.
		 * @param message the exception message.
		 * @param cause the cause of this exception.
		 */
		public SchemaUpdateException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}
}
