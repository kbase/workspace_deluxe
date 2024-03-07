package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableMap;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public class GridFSBlobStoreTest {

	private static GridFSBlobStore gfsb;
	private static GridFSBucket gfs;
	private static MongoController mongo;

	private static final String a32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " +
				mongo.getTempDir());
		TestCommon.stfuLoggers();
		final MongoClient mongoClient = MongoClients.create("mongodb://localhost:" + mongo.getServerPort());
		final MongoDatabase db = mongoClient.getDatabase("GridFSBackendTest");
		gfs = GridFSBuckets.create(db);
		gfsb = new GridFSBlobStore(db);

	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Test
	public void failConstruct() throws Exception {
		try {
			new GridFSBlobStore(null);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new NullPointerException("db"));
		}
	}

	@Test
	public void badInput() throws Exception {
		try {
			gfsb.saveBlob(new MD5(a32), null, true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}

		try {
			gfsb.saveBlob(null, new StringRestreamable("foo"), true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}

	@Test
	public void sortMarker() throws Exception {
		// Tests various ways the sort marker might be represented.
		// The first two cases should never really happen in practice unless the
		// v0.12.0+ workspace is started on old data with a GridFS backend, in which case
		// the first case is possible (and sort will always be false whether the data is sorted
		// or not).
		// The second case should never ever happen but we test because why not
		final Map<GridFSUploadOptions, Boolean> testCases = ImmutableMap.of(
				new GridFSUploadOptions(), false,
				new GridFSUploadOptions().metadata(new Document()), false,
				new GridFSUploadOptions().metadata(new Document("sorted", false)), false,
				new GridFSUploadOptions().metadata(new Document("sorted", true)), true
				);

		final String s = "pootypoot";
		final MD5 md5 = new MD5(a32);
		for (final Entry<GridFSUploadOptions, Boolean> tcase: testCases.entrySet()) {
			gfs.uploadFromStream(
					new BsonString(md5.getMD5()),
					md5.getMD5(),
					new ByteArrayInputStream(s.getBytes("UTF-8")),
					tcase.getKey());

			final ByteArrayFileCache d = gfsb.getBlob(md5, new ByteArrayFileCacheManager());
			assertThat("data returned marked as unsorted", d.isSorted(), is(tcase.getValue()));
			final String returned = IOUtils.toString(d.getJSON());
			assertThat("Didn't get same data back from store", returned, is(s));
			gfsb.removeBlob(md5);
		}
	}

	private static class StringRestreamable implements Restreamable {

		private final String data;

		public StringRestreamable(final String data) {
			this.data = data;
		}
		@Override
		public InputStream getInputStream() {
			return IOUtils.toInputStream(data);
		}
		@Override
		public long getSize() {
			return -1; //unused for GFS
		}
	}

	@Test
	public void saveAndGetBlob() throws Exception {
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		gfsb.saveBlob(md1, new StringRestreamable(data), true);
		MD5 md1copy = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		ByteArrayFileCache d = gfsb.getBlob(md1copy, new ByteArrayFileCacheManager());
		assertThat("data returned marked as sorted", d.isSorted(), is(true));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(data));
		gfsb.saveBlob(md1, new StringRestreamable(data), true); //should be able to save the same thing twice with no error

		gfsb.saveBlob(md1, new StringRestreamable(data), false); //this should do nothing
		assertThat("sorted still true", gfsb.getBlob(md1copy, new ByteArrayFileCacheManager())
					.isSorted(), is(true));

		MD5 md2 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2");
		String data2 = "this is also a blob yo";
		gfsb.saveBlob(md2, new StringRestreamable(data2), false);
		d = gfsb.getBlob(md2, new ByteArrayFileCacheManager());
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));

		gfsb.removeBlob(md1);
		gfsb.removeBlob(md2);
	}

	private class FailOnCloseInputStream extends InputStream {

		private final InputStream wrapped;

		public FailOnCloseInputStream(final InputStream wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public int read() throws IOException {
			return wrapped.read();
		}

		@Override
		public void close() throws IOException {
			throw new IOException("oh drat.");
		}
	}

	@Test
	public void saveFailIOOnClose() throws Exception {
		// throwing a IOError on reading from a stream makes gridfs throw
		//    new Runtime("i'm doing something wrong")
		final Restreamable rs = mock(Restreamable.class);
		when(rs.getInputStream()).thenReturn(new FailOnCloseInputStream(
				new ByteArrayInputStream("a".getBytes())));

		try {
			gfsb.saveBlob(new MD5(a32), rs, true);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new BlobStoreCommunicationException(
					"Couldn't connect to the GridFS backend: oh drat."));
		}
	}

	@Test
	public void getNonExistantBlob() throws Exception {
		try {
			gfsb.getBlob(new MD5(a32), new ByteArrayFileCacheManager());
			fail("getblob should throw exception");
		} catch (NoSuchBlobException nsbe) {
			assertThat("wrong exception message from failed getblob", nsbe.getLocalizedMessage(),
					is("Attempt to retrieve non-existant blob with chksum " + a32));
			assertThat("incorrect md5", nsbe.getMD5(), is(new MD5(a32)));
		}
	}

	@Test
	public void removeNonExistantBlob() throws Exception {
		gfsb.removeBlob(new MD5(a32)); //should silently not remove anything
	}

	@Test
	public void status() throws Exception {
		List<DependencyStatus> deps = gfsb.status();
		assertThat("incorrect number of deps", deps.size(), is(1));
		DependencyStatus dep = deps.get(0);
		assertThat("incorrect fail", dep.isOk(), is(true));
		assertThat("incorrect name", dep.getName(), is("GridFS"));
		assertThat("incorrect status", dep.getStatus(), is("OK"));
		//should throw an error if not a semantic version
		Version.valueOf(dep.getVersion());
	}
}
