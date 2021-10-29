package us.kbase.workspace.test.database.mongo;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;

import com.mongodb.client.MongoDatabase;

import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;

/** Create a {@link MongoWorkspaceDB} instance with a real Mongo DB but all other dependencies
 * mocked. Useful for manipulating clock times or when actual object data is not involved in
 * tests.
 *
 */
public class PartialMock {
	
	public final MongoWorkspaceDB mdb;
	public final BlobStore bsmock;
	public final TempFilesManager tfmmock;
	public final Clock clockmock;
	
	public PartialMock(final MongoDatabase db) {
		bsmock = mock(BlobStore.class);
		tfmmock = mock(TempFilesManager.class);
		clockmock = mock(Clock.class);
		Constructor<MongoWorkspaceDB> con;
		try {
			con = MongoWorkspaceDB.class.getDeclaredConstructor(
					MongoDatabase.class, BlobStore.class, TempFilesManager.class, Clock.class);
			con.setAccessible(true);
			mdb = con.newInstance(db, bsmock, tfmmock, clockmock);
		} catch (NoSuchMethodException | SecurityException | InstantiationException |
				IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(
					"MongoWorkspaceDB instance creation failed: " + e.getLocalizedMessage(), e);
		}
		
	}
}
