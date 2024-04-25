package us.kbase.test.workspace;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;

public class WorkspaceMongoIndex {

    public static Set<Document> getAndNormalizeIndexes(final MongoDatabase db, final String collectionName) {
        final Set<Document> indexes = new HashSet<>();
        for (final Document index: db.getCollection(collectionName).listIndexes()) {
            // In MongoDB 4.4, the listIndexes and the mongo shell helper method db.collection.getIndexes()
            // no longer returns the namespace ns field in the index specification documents.
            index.remove("ns");
            // some versions of Mongo return ints, some longs. Convert all to longs.
            if (index.containsKey("expireAfterSeconds")) {
                index.put("expireAfterSeconds", ((Number) index.get("expireAfterSeconds")).longValue());
            }
            indexes.add(index);
        }
        return indexes;
    }
}
