package us.kbase.workspace.test.database.mongo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.set;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import com.mongodb.client.MongoDatabase;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.ExtractedMetadata;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.BlobStore;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.database.provenance.Provenance;

/** Create a {@link MongoWorkspaceDB} instance with a real Mongo DB but all other dependencies
 * mocked. Useful for manipulating clock times or when actual object data is not involved in
 * tests.
 *
 */
public class PartialMock {

	public final MongoWorkspaceDB mdb;
	public final BlobStore bsmock;
	public final Clock clockmock;

	public PartialMock(final MongoDatabase db) {
		bsmock = mock(BlobStore.class);
		clockmock = mock(Clock.class);
		Constructor<MongoWorkspaceDB> con;
		try {
			con = MongoWorkspaceDB.class.getDeclaredConstructor(
					MongoDatabase.class, BlobStore.class, Clock.class);
			con.setAccessible(true);
			mdb = con.newInstance(db, bsmock, clockmock);
		} catch (NoSuchMethodException | SecurityException | InstantiationException |
				IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(
					"MongoWorkspaceDB instance creation failed: " + e.getLocalizedMessage(), e);
		}
	}

	public Reference saveTestObject(
			final ResolvedWorkspaceID wsid,
			final WorkspaceUser u,
			final Provenance prov,
			final String name,
			final String absoluteTypeDef,
			final String md5,
			final long size)
			throws Exception {
		final ValidatedTypedObject vto = mock(ValidatedTypedObject.class);
		when(vto.getValidationTypeDefId()).thenReturn(
				AbsoluteTypeDefId.fromAbsoluteTypeString(absoluteTypeDef));
		when(vto.extractMetadata(16000)).thenReturn(new ExtractedMetadata(Collections.emptyMap()));
		when(vto.getMD5()).thenReturn(new MD5(md5));
		when(vto.getRelabeledSize()).thenReturn(size);

		final List<ObjectInformation> res = mdb.saveObjects(u, wsid,
				Arrays.asList(new WorkspaceSaveObject(
						new ObjectIDNoWSNoVer(name),
						new UObject(ImmutableMap.of("foo", "bar")),
						new TypeDefId("DroppedAfter.Resolve", "1.0"),
						null,
						prov,
						false)
						.resolve(
								wsid,
								vto,
								set(),
								Collections.emptyList(),
								Collections.emptyMap())
						));
		return res.get(0).getReferencePath().get(0);
	}
}
