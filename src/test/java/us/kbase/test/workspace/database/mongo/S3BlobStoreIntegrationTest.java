package us.kbase.test.workspace.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.common.TestCommon;
import us.kbase.test.workspace.controllers.minio.MinioController;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.S3BlobStore;
import us.kbase.workspace.database.mongo.S3ClientWithPresign;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public class S3BlobStoreIntegrationTest {

	// TODO TEST test that indexes are correct

	// probably some way to make the individual tests run on the 3 different store setups.
	// tests are very similar
	// parameterized tests are crappy because eclipse can't run a single test anymore for
	// some reason.

	private static S3BlobStore s3bs;
	// use to exercise the cert trusting code, although doesn't actually test against self
	//signed certs
	private static S3BlobStore s3bsTrustCerts;
	private static S3ClientWithPresign s3client;
	private static MongoDatabase mongo;
	private static MinioController minio;
	private static MongoController mongoCon;

	private static final String A32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final String COLLECTION = "minio_blobs";
	private static final String BUCKET = "test-bucket";

	@BeforeClass
	public static void setUpClass() throws Exception {
		TestCommon.stfuLoggers();
		mongoCon = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + mongoCon.getTempDir());
		System.out.println("Started mongo server at localhost:" + mongoCon.getServerPort());

		MongoClient mongoClient = MongoClients.create("mongodb://localhost:" + mongoCon.getServerPort());
		mongo = mongoClient.getDatabase("MinioBackendTest");

		minio = new MinioController(
				TestCommon.getMinioExe(),
				"s3keyhere",
				"sooporsekrit",
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Minio temp dir " + minio.getTempDir());
		URL url = new URL("http://localhost:" + minio.getServerPort());
		System.out.println("Testing workspace Minio backend pointed at: " + url);
		s3client = new S3ClientWithPresign(
				url, "s3keyhere", "sooporsekrit", Region.of("us-west-1"), false);
		s3bs = new S3BlobStore(mongo.getCollection(COLLECTION), s3client, BUCKET);

		final S3ClientWithPresign s3client2 = new S3ClientWithPresign(
				url, "s3keyhere", "sooporsekrit", Region.of("us-west-1"), true);
		s3bsTrustCerts = new S3BlobStore(mongo.getCollection(COLLECTION), s3client2, BUCKET);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		if (minio != null) {
			minio.destroy(TestCommon.getDeleteTempFiles());
		}
		if (mongoCon != null) {
			mongoCon.destroy(TestCommon.getDeleteTempFiles());
		}
	}

	@Test
	public void constructClientFailBadInput() throws Exception {
		final URL u = new URL("http://localhost:45678");
		final String k = "k";
		final String s = "s";
		final Region r = Region.of("us-west-1");

		constructClientFail(null, k, s, r, new NullPointerException("host"));
		constructClientFail(new URL("http://local^host:45678"), k, s, r, new URISyntaxException(
				"http://local^host:45678", "Illegal character in authority at index 7"));
		constructClientFail(u, null, s, r, new IllegalArgumentException(
				"s3key cannot be null or whitespace only"));
		constructClientFail(u, "   \t    ", s, r, new IllegalArgumentException(
				"s3key cannot be null or whitespace only"));
		constructClientFail(u, k, null, r, new IllegalArgumentException(
				"s3secret cannot be null or whitespace only"));
		constructClientFail(u, k, "   \t    ", r, new IllegalArgumentException(
				"s3secret cannot be null or whitespace only"));
		constructClientFail(u, k, s, null, new NullPointerException("region"));
	}

	private void constructClientFail(
			final URL host,
			final String key,
			final String secret,
			final Region region,
			final Exception expected) {
		try {
			new S3ClientWithPresign(host, key, secret, region, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void uploadPresignFailBadInput() throws Exception {
		final PutObjectRequest p = PutObjectRequest.builder().bucket("b").key("k").build();
		final Restreamable r = new StringRestreamable("foo");

		uploadPresignFail(null, r, new NullPointerException("put"));
		uploadPresignFail(p, null, new NullPointerException("object"));

	}

	private void uploadPresignFail(
			final PutObjectRequest put,
			final Restreamable object,
			final Exception expected) {
		try {
			s3client.presignAndPutObject(put, object);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	static class StringRestreamable implements Restreamable {

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
			return (long) data.getBytes().length;
		}
	}

	@Test
	public void saveAndGetBlob() throws Exception {
		saveAndGetBlob(s3bs);
		saveAndGetBlob(s3bsTrustCerts);
	}

	private void saveAndGetBlob(final S3BlobStore bs) throws Exception {
		MD5 md1 = new MD5("5e498cecc4017dad15313bb009b0ef49");
		String data = "this is a blob yo";
		bs.saveBlob(md1, new StringRestreamable(data), true);
		MD5 md1copy = new MD5("5e498cecc4017dad15313bb009b0ef49");
		ByteArrayFileCache d = s3bs.getBlob(md1copy, new ByteArrayFileCacheManager());
		assertThat("data returned marked as sorted", d.isSorted(), is(true));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(data));
		//should be able to save the same thing twice with no error
		bs.saveBlob(md1, new StringRestreamable(data), true);

		bs.saveBlob(md1, new StringRestreamable(data), false); //this should do nothing
		assertThat("sorted still true", s3bs.getBlob(md1copy, new ByteArrayFileCacheManager())
				.isSorted(), is(true));

		MD5 md2 = new MD5("78afe93c486269db5b49d9017e850103");
		String data2 = "this is also a blob yo";
		bs.saveBlob(md2, new StringRestreamable(data2), false);
		d = bs.getBlob(md2, new ByteArrayFileCacheManager());
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));

		bs.removeBlob(md1);
		bs.removeBlob(md2);
		failGetBlob(md1);
	}

	@Test
	public void getNonExistantBlob() throws Exception {
		failGetBlob(new MD5(A32));
	}

	private void failGetBlob(MD5 md5) throws Exception {
		try {
			s3bs.getBlob(md5, new ByteArrayFileCacheManager());
			fail("getblob should throw exception");
		} catch (NoSuchBlobException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("No blob saved with chksum "
					+ md5.getMD5()));
		}
	}

	@Test
	public void removeNonExistantBlob() throws Exception {
		s3bs.removeBlob(new MD5(A32)); //should silently not remove anything

	}

	@Test
	public void status() throws Exception {
		List<DependencyStatus> deps = s3bs.status();
		assertThat("incorrect number of deps", deps.size(), is(1));
		DependencyStatus dep = deps.get(0);
		assertThat("incorrect fail", dep.isOk(), is(true));
		assertThat("incorrect name", dep.getName(), is("S3"));
		assertThat("incorrect status", dep.getStatus(), is("OK"));
		assertThat("incorrect version", dep.getVersion(), is("Unknown"));
	}

	@Test
	public void presignUploadFail() throws Exception {
		try {
			s3client.getClient().deleteBucket(
					DeleteBucketRequest.builder().bucket(BUCKET).build());
			try {
				MD5 md1 = new MD5("5e498cecc4017dad15313bb009b0ef49");
				String data = "this is a blob yo";
				s3bs.saveBlob(md1, new StringRestreamable(data), true);
			} catch (BlobStoreCommunicationException e) {
				assertThat("incorrect exception", e.getMessage(), startsWith(
						"S3 error: Error saving file to S3 (404), truncated response " +
						"follows:\n<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
						"<Error><Code>NoSuchBucket</Code><Message>The specified bucket does " +
						"not exist</Message><Key>"));

				assertThat("incorrect exception", e.getMessage(), containsString(
						"</Key><BucketName>test-bucket</BucketName><Resource>"));
			}
		} finally {
			s3bs = new S3BlobStore(mongo.getCollection(COLLECTION), s3client, BUCKET);
		}
	}
}
