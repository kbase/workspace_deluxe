package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.assertLogEventsCorrect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.TestCommon.LogEvent;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.mongo.S3BlobStore;
import us.kbase.workspace.database.mongo.S3BlobStore.UUIDGen;
import us.kbase.workspace.database.mongo.S3ClientWithPresign;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.UpdateOptionsMatcher;

public class S3BlobStoreTest {
	
	// strictly unit tests. Integration tests are in another class.
	
	private static final String BUCKET63;
	static {
		final StringBuilder sb = new StringBuilder();
		sb.append("abcefghijklmnopqrstuvwxyz-0123456789-012456");
		for (int i = 0; i < 2; i++) {
			sb.append("0123456789");
		}
		BUCKET63 = sb.toString();
		assertThat("incorrect length", BUCKET63.length(), is(63));
	}
	
	private static List<ILoggingEvent> logEvents;
	
	@BeforeClass
	public static void beforeClass() {
		logEvents = TestCommon.setUpSLF4JTestLoggerAppender("us.kbase.workspace");
	}
	
	@Before
	public void before() {
		logEvents.clear();
	}
	
	private static class Mocks {
		final S3ClientWithPresign s3clipre;
		final S3Client s3cli;
		final MongoCollection<Document> col;
		final FindIterable<Document> cur;
		final UUIDGen uuidGen;
		
		public Mocks() {
			s3clipre = mock(S3ClientWithPresign.class);
			s3cli = mock(S3Client.class);
			when(s3clipre.getClient()).thenReturn(s3cli);
			@SuppressWarnings("unchecked")
			final MongoCollection<Document> col = mock(MongoCollection.class);
			this.col = col;
			@SuppressWarnings("unchecked")
			final FindIterable<Document> cur = mock(FindIterable.class);
			this.cur = cur;
			uuidGen = mock(UUIDGen.class);
		}
	}
	
	@Test
	public void constructWithNewBucket() throws Exception {
		final Mocks m = new Mocks();
		
		new S3BlobStore(m.col, m.s3clipre, "   \t   " + BUCKET63 + "    ");

		verify(m.s3cli).createBucket(CreateBucketRequest.builder().bucket(BUCKET63).build());
	}
	
	@Test
	public void constructWithExistingBucket() throws Exception {
		final Mocks m = new Mocks();
		
		when(m.s3cli.createBucket(CreateBucketRequest.builder().bucket("foo").build()))
			.thenThrow(BucketAlreadyOwnedByYouException.builder().message("whoopsie").build());
		
		new S3BlobStore(m.col, m.s3clipre, "   foo     ");
		// test passes since no exception thown
	}
	
	@Test
	public void constructFailBadInput() throws Exception {
		final Mocks m = new Mocks();
		
		constructFail(null, m.s3clipre, "sss", new NullPointerException("mongoCollection"));
		constructFail(m.col, null, "sss", new NullPointerException("s3"));
		constructFail(m.col, m.s3clipre, null, new IllegalArgumentException(
				"bucket cannot be null or whitespace only"));
		constructFail(m.col, m.s3clipre, "   \t     ", new IllegalArgumentException(
				"bucket cannot be null or whitespace only"));
		constructFail(m.col, m.s3clipre, "   fo     ", new IllegalArgumentException(
				"bucket length must be between 3 and 63 characters"));
		constructFail(m.col, m.s3clipre, BUCKET63 + "a", new IllegalArgumentException(
				"bucket length must be between 3 and 63 characters"));
		constructFail(m.col, m.s3clipre, "-ab", new IllegalArgumentException(
				"bucket must start with a letter or number"));
		constructFail(m.col, m.s3clipre, "a𐤈b", new IllegalArgumentException(
				"bucket contains an illegal character: 𐤈"));
		constructFail(m.col, m.s3clipre, "aCb", new IllegalArgumentException(
				"bucket contains an illegal character: C"));
		constructFail(m.col, m.s3clipre, "a#b", new IllegalArgumentException(
				"bucket contains an illegal character: #"));
	}
	
	@Test
	public void constructFailSDKError() throws Exception {
		final Mocks m = new Mocks();
		
		when(m.s3cli.createBucket(CreateBucketRequest.builder().bucket("buk").build()))
			.thenThrow(SdkException.builder().message("whoopsie").build());
		
		constructFail(m.col, m.s3clipre, "buk", new BlobStoreCommunicationException(
				"Failed to initialize S3 bucket: whoopsie"));
	}
	
	private void constructFail(
			final MongoCollection<Document> col,
			final S3ClientWithPresign cli,
			final String bucket,
			final Exception expected) {
		try {
			new S3BlobStore(col, cli, bucket);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private static class TestRestreamable implements Restreamable {
		private final String data;
		
		private TestRestreamable(final String data) {
			this.data = data;
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public long getSize() {
			return data.getBytes(StandardCharsets.UTF_8).length;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			TestRestreamable other = (TestRestreamable) obj;
			if (data == null) {
				if (other.data != null) {
					return false;
				}
			} else if (!data.equals(other.data)) {
				return false;
			}
			return true;
		}
	}
	
	@Test
	public void saveBlobAlreadyExists() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s3 = new S3BlobStore(m.col, m.s3clipre, "foo");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
					.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
					.append("sorted", true));
		
		s3.saveBlob(new MD5("1fc5a11811de5142af444f5d482cd748"), new TestRestreamable("f"), true);
		
		verify(m.s3cli).createBucket(CreateBucketRequest.builder().bucket("foo").build());
		verifyNoMoreInteractions(m.s3cli);
		verify(m.s3clipre, never()).presignAndPutObject(any(), any());
		verify(m.col, never()).updateOne(
				any(Document.class), any(Document.class), any(UpdateOptions.class));
	}
	
	@Test
	public void saveBlobSorted() throws Exception {
		saveBlob(new TestRestreamable("f"), new TestRestreamable("f"), true);
	}
	
	@Test
	public void saveBlobUnSorted() throws Exception {
		saveBlob(new TestRestreamable("yay"), new TestRestreamable("yay"), false);
	}

	private void saveBlob(
			final Restreamable data,
			final Restreamable expecteddata,
			final boolean sorted)
			throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s3 = new S3BlobStore(m.col, m.s3clipre, "foo", m.uuidGen);
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
			.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		
		when(m.uuidGen.randomUUID()).thenReturn(UUID.fromString(
				"68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9"));
		
		when(m.s3cli.headObject(HeadObjectRequest.builder().bucket("foo").key(
				"68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build())).thenReturn(
				HeadObjectResponse.builder()
						.eTag("   \"   1fc5a11811de5142af444f5d482cd748\"  ").build());
		
		s3.saveBlob(new MD5("1fc5a11811de5142af444f5d482cd748"), data, sorted);

		verify(m.s3clipre).presignAndPutObject(
				PutObjectRequest.builder()
						.bucket("foo")
						.key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.build(),
				expecteddata);
		verify(m.col).updateOne(
				eq(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")),
				eq(new Document("$set",
						new Document("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
								.append("sorted", sorted))),
				argThat(new UpdateOptionsMatcher(new UpdateOptions().upsert(true))));
	}
	
	@Test
	public void saveBlobFailBadInput() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		saveBlobFail(s, null, r, new NullPointerException("md5"));
		saveBlobFail(s, md5, null, new NullPointerException("data"));
	}
	
	@Test
	public void saveBlobFailMongoExceptionOnFind() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenThrow(new MongoException("well rats"));
		
		saveBlobFail(s, md5, r, new BlobStoreCommunicationException(
				"Could not read from the mongo database"));
	}
	
	@Test
	public void saveBlobFailOnPresign() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo", m.uuidGen);
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		when(m.uuidGen.randomUUID()).thenReturn(UUID.fromString(
				"68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9"));
		doThrow(new IOException("get your trash data outta here")).when(m.s3clipre)
				.presignAndPutObject(
						PutObjectRequest.builder()
								.bucket("foo")
								.key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
								.build(),
						r);
		
		saveBlobFail(s, md5, r, new BlobStoreCommunicationException(
				"S3 error: get your trash data outta here"));
	}
	
	@Test
	public void saveBlobFailOnHeadObject() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo", m.uuidGen);
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		when(m.uuidGen.randomUUID()).thenReturn(UUID.fromString(
				"68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9"));
		when(m.s3cli.headObject(HeadObjectRequest.builder().bucket("foo").key(
				"68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build())).thenThrow(
						SdkException.builder().message("how dare you... how DARE you!").build());
		
		saveBlobFail(s, md5, r, new BlobStoreCommunicationException(
				"Error stating S3 object: how dare you... how DARE you!"));
	}
	
	@Test
	public void saveBlobFailBadMD5() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo", m.uuidGen);
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		
		when(m.uuidGen.randomUUID()).thenReturn(UUID.fromString(
				"68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9"));

		when(m.s3cli.headObject(HeadObjectRequest.builder().bucket("foo").key(
				"68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build())).thenReturn(
				HeadObjectResponse.builder()
						.eTag("   \"   1fc5a11811de5142af444f5d482cd749\"  ").build());
		
		saveBlobFail(s, md5, r, new BlobStoreCommunicationException(
				"S3 upload corrupted, MD5s don't match"));
	}
	
	@Test
	public void saveBlobFailOnMongoUpdate() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo", m.uuidGen);
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final Restreamable r = new TestRestreamable("f");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		
		when(m.uuidGen.randomUUID()).thenReturn(UUID.fromString(
				"68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9"));

		when(m.s3cli.headObject(HeadObjectRequest.builder().bucket("foo").key(
				"68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build())).thenReturn(
				HeadObjectResponse.builder()
						.eTag("   \"   1fc5a11811de5142af444f5d482cd748\"  ").build());
		
		when(m.col.updateOne(
				eq(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")),
				eq(new Document("$set",
						new Document("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
							.append("sorted", true))),
				argThat(new UpdateOptionsMatcher(new UpdateOptions().upsert(true)))
				)).thenThrow(new MongoException("dang!"));
		
		saveBlobFail(s, md5, r, new BlobStoreCommunicationException(
				"Could not write to the mongo database"));
	}
	
	private void saveBlobFail(
			final S3BlobStore s3,
			final MD5 md5,
			final Restreamable data,
			final Exception expected) {
		try {
			s3.saveBlob(md5, data, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void getBlob() throws Exception {
		getBlob(true);
		getBlob(false);
	}

	private void getBlob(final boolean sorted) throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", sorted));
		
		when(m.s3cli.getObject(GetObjectRequest.builder().bucket("foo")
				.key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build()))
			.thenReturn(new ResponseInputStream<GetObjectResponse>(
					GetObjectResponse.builder().build(), // not currently used
					AbortableInputStream.create(
							new ByteArrayInputStream("\"input here\"".getBytes()))));
		
		final ByteArrayFileCacheManager bafcMan = new ByteArrayFileCacheManager();
		
		final ByteArrayFileCache ba = s.getBlob(md5, bafcMan);
		
		assertThat("incorrect data", ba.getUObject().asClassInstance(String.class),
				is("input here"));
		assertThat("incorrect sorted", ba.isSorted(), is(sorted));
		assertThat("incorrect is trusted json", ba.containsTrustedJson(), is(true));
	}
	
	@Test
	public void getBlobFailBadInput() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		final ByteArrayFileCacheManager bafcMan = new ByteArrayFileCacheManager();
		
		getBlobFail(s, null, bafcMan, new NullPointerException("md5"));
		getBlobFail(s, md5, null, new NullPointerException("bafcMan"));
	}
	
	@Test
	public void getBlobFailNoBlob() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);
		
		final ByteArrayFileCacheManager bafcMan = new ByteArrayFileCacheManager();
		final Exception e = getBlobFail(s, md5, bafcMan, new NoSuchBlobException(
				"No blob saved with chksum 1fc5a11811de5142af444f5d482cd748",
				new MD5("1fc5a11811de5142af444f5d482cd748")));
		assertThat("incorrect md5", ((NoSuchBlobException) e).getMD5(),
				is(new MD5("1fc5a11811de5142af444f5d482cd748")));
	}
	
	@Test
	public void getBlobFailMongoException() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenThrow(new MongoException("heck"));
		
		final ByteArrayFileCacheManager bafcMan = new ByteArrayFileCacheManager();
		getBlobFail(s, md5, bafcMan, new BlobStoreCommunicationException(
				"Could not read from the mongo database"));
	}
	
	@Test
	public void getBlobFailGetObject() throws Exception {
		getBlobFailGetObject(SdkException.builder().message("ok doody butt").build(),
				new BlobStoreCommunicationException("Error getting S3 object: ok doody butt"));
		getBlobFailGetObject(NoSuchKeyException.builder().message("ok doody butt").build(),
				new BlobStoreCommunicationException("Inconsistent MongoDB and S3 records for " +
						"MD5 1fc5a11811de5142af444f5d482cd748"));
	}

	private void getBlobFailGetObject(final SdkException thrown, final Exception expected)
			throws BlobStoreCommunicationException {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", true));
		
		when(m.s3cli.getObject(GetObjectRequest.builder().bucket("foo")
				.key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build()))
			.thenThrow(thrown);
		
		final ByteArrayFileCacheManager bafcMan = new ByteArrayFileCacheManager();
		getBlobFail(s, md5, bafcMan, expected);
	}
	
	@Test
	public void getBlobFailCreateBAFC() throws Exception {
		final Mocks m = new Mocks();
		final ByteArrayFileCacheManager bafcm = mock(ByteArrayFileCacheManager.class);
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", true));
		
		when(m.s3cli.getObject(GetObjectRequest.builder().bucket("foo")
				.key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build()))
			.thenReturn(new ResponseInputStream<GetObjectResponse>(
					GetObjectResponse.builder().build(), // not currently used
					AbortableInputStream.create(
							new ByteArrayInputStream("\"input here\"".getBytes()))));
		
		when(bafcm.createBAFC(any(), eq(true), eq(true)))
				.thenThrow(new IOException("doody butts"));

		getBlobFail(s, md5, bafcm, new BlobStoreCommunicationException(
				"IO Error accessing blob: doody butts"));
	}
	
	private Exception getBlobFail(
			final S3BlobStore s,
			final MD5 md5,
			final ByteArrayFileCacheManager man,
			final Exception expected) {
		try {
			s.getBlob(md5, man);
			fail("expected exception");
			return null; // can't actually get here...
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}
	
	@Test
	public void removeBlobNoBlob() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(null);

		s.removeBlob(md5);
		
		verify(m.s3cli).createBucket(CreateBucketRequest.builder().bucket("foo").build());
		verifyNoMoreInteractions(m.s3cli);
		verify(m.s3clipre, never()).presignAndPutObject(any(), any());
		verify(m.col, never()).deleteOne(any());
	}
	
	@Test
	public void removeBlob() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", true));

		s.removeBlob(md5);
		
		verify(m.col).deleteOne(new Document("chksum", "1fc5a11811de5142af444f5d482cd748"));
		verify(m.s3cli).deleteObject(DeleteObjectRequest.builder()
				.bucket("foo").key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build());
	}
	
	@Test
	public void removeBlobFailBadInput() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		
		removeBlobFail(s, null, new NullPointerException("md5"));
	}
	
	@Test
	public void removeBlobFailMongoExceptionOnFind() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenThrow(new MongoException("heck"));

		removeBlobFail(s, md5, new BlobStoreCommunicationException(
				"Could not read from the mongo database"));
	}
	
	@Test
	public void removeBlobFailMongoExceptionOnRemove() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", true));

		when(m.col.deleteOne(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenThrow(new MongoException("hey ho"));
		
		removeBlobFail(s, md5, new BlobStoreCommunicationException(
				"Could not write to the mongo database"));
	}
	
	@Test
	public void removeBlobFailMongoExceptionOnDeleteObject() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		final MD5 md5 = new MD5("1fc5a11811de5142af444f5d482cd748");
		
		when(m.col.find(new Document("chksum", "1fc5a11811de5142af444f5d482cd748")))
				.thenReturn(m.cur);
		when(m.cur.first()).thenReturn(
				new Document("chksum", "1fc5a11811de5142af444f5d482cd748")
						.append("key", "68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9")
						.append("sorted", true));
		
		when(m.s3cli.deleteObject(DeleteObjectRequest.builder()
				.bucket("foo").key("68/47/1b/68471ba8-c6b3-4ab7-9fc1-3c9ff304d6d9").build()))
				.thenThrow(SdkException.builder().message("my pants are on fire").build());

		removeBlobFail(s, md5, new BlobStoreCommunicationException(
				"Failed to delete blob: my pants are on fire"));
	}
	
	private void removeBlobFail(final S3BlobStore s, final MD5 md5, final Exception expected) {
		try {
			s.removeBlob(md5);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void status() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		
		final List<DependencyStatus> stat = s.status();
		
		assertThat("incorrect status size", stat.size(), is(1));
		final DependencyStatus ds = stat.get(0);
		assertThat("incorrect name", ds.getName(), is("S3"));
		assertThat("incorrect status", ds.getStatus(), is("OK"));
		assertThat("incorrect version", ds.getVersion(), is("Unknown"));
		assertThat("incorrect version", ds.isOk(), is(true));
	}
	
	@Test
	public void statusFail() throws Exception {
		final Mocks m = new Mocks();
		
		final S3BlobStore s = new S3BlobStore(m.col, m.s3clipre, "foo");
		
		when(m.s3cli.headBucket(HeadBucketRequest.builder().bucket("foo").build()))
				.thenThrow(SdkException.builder().message("You done f'd up now").build());
		
		final List<DependencyStatus> stat = s.status();
		
		assertThat("incorrect status size", stat.size(), is(1));
		final DependencyStatus ds = stat.get(0);
		assertThat("incorrect name", ds.getName(), is("S3"));
		assertThat("incorrect status", ds.getStatus(),
				is("Failed to connect to S3: You done f'd up now"));
		assertThat("incorrect version", ds.getVersion(), is("Unknown"));
		assertThat("incorrect version", ds.isOk(), is(false));
		
		assertLogEventsCorrect(logEvents, new LogEvent(Level.ERROR,
				"Failed to connect to S3", S3BlobStore.class,
				SdkException.builder().message("You done f'd up now").build()));
	}

}
