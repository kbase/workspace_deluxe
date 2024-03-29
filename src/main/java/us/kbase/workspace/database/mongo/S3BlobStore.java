package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.UpdateOptions;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
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
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.Restreamable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

/** Blob store file storage using S3 as the backend.
 * @author gaprice@lbl.gov
 *
 */
public class S3BlobStore implements BlobStore {
	
	/** An interface for generating a random UUID. Used for mocking UUID generation in unit tests.
	 * @author gaprice@lbl.gov
	 *
	 */
	public interface UUIDGen {
		
		/** Generates a random UUID identically to {@link UUID#randomUUID()}.
		 * @return a UUID.
		 */
		UUID randomUUID();
	}
	
	private final MongoCollection<Document> col;
	private final S3ClientWithPresign s3;
	private final String bucket;
	private final UUIDGen uuidGen;
	
	/** Create the blob store.
	 * @param mongoCollection the MongoDB collection in which the blob store will store records.
	 * @param s3 the S3 client.
	 * @param bucket the name of the bucket in which files will be stored.
	 * @throws BlobStoreCommunicationException if an error occurs contacting the S3 backend.
	 * @throws IllegalArgumentException if the bucket name is illegal.
	 */
	public S3BlobStore(
			final MongoCollection<Document> mongoCollection,
			final S3ClientWithPresign s3,
			final String bucket)
			throws BlobStoreCommunicationException, IllegalArgumentException {
		this(mongoCollection, s3, bucket, new UUIDGen() {
			@Override
			public UUID randomUUID() {
				return UUID.randomUUID();
			}
		});
	}
	
	/** This constructor is to be used only for unit testing, as it allows mocking the UUID
	 * generator. It is otherwise equivalent to
	 * {@link #S3BlobStore(MongoCollection, S3ClientWithPresign, String)}.
	 */
	public S3BlobStore(
			final MongoCollection<Document> mongoCollection,
			final S3ClientWithPresign s3,
			final String bucket,
			final UUIDGen uuidGen)
			throws BlobStoreCommunicationException, IllegalArgumentException {
		this.uuidGen = uuidGen;
		this.col = requireNonNull(mongoCollection, "mongoCollection");
		this.s3 = requireNonNull(s3, "s3");
		this.bucket = checkBucketName(bucket);
		this.col.createIndex(new Document(Fields.S3_CHKSUM, 1), new IndexOptions().unique(true));
		try {
			s3.getClient().createBucket(CreateBucketRequest.builder().bucket(this.bucket).build());
		} catch (BucketAlreadyOwnedByYouException e) {
			// do nothing, we're groovy
		} catch (SdkException e) {
			throw new BlobStoreCommunicationException("Failed to initialize S3 bucket: " +
					e.getMessage(), e);
		}
		//TODO DBCONSIST check that a few files exist to ensure we're pointing at the right S3 instance
	}
	
	private String checkBucketName(String bucket) throws IllegalArgumentException {
		bucket = checkString(bucket, "bucket");
		if (bucket.length() < 3 || bucket.length() > 63) {
			throw new IllegalArgumentException(
					"bucket length must be between 3 and 63 characters");
		}
		final boolean[] first = new boolean[]{true};
		bucket.codePoints().forEach(cp -> {
			if (cp > 127) {
				throw new IllegalArgumentException("bucket contains an illegal character: " +
						new String(new int[]{cp}, 0, 1));
			}
			final char c = (char) cp;
			if (first[0] && c == '-') {
				throw new IllegalArgumentException("bucket must start with a letter or number");
			}
			first[0] = false;
			if (!Character.isLowerCase(c) && !Character.isDigit(c) && c != '-') {
				throw new IllegalArgumentException("bucket contains an illegal character: " + c);
			}
		});
		return bucket;
	}

	@Override
	public void saveBlob(final MD5 md5, final Restreamable data, final boolean sorted)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException {
		requireNonNull(md5, "md5");
		requireNonNull(data, "data");
		try {
			getBlobEntry(md5);
			return; //already saved
		} catch (NoSuchBlobException nb) {
			//go ahead, need to save
		}
		final String key = toS3Key(uuidGen.randomUUID());
		try {
			s3.presignAndPutObject(
					PutObjectRequest.builder().bucket(bucket).key(key).build(),
					data);
		} catch (IOException e) {
			throw new BlobStoreCommunicationException("S3 error: " + e.getMessage(), e);
		}
		try {
			final HeadObjectResponse obj = s3.getClient().headObject(HeadObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.build());
			if (!obj.eTag().replace("\"", "").trim().equals(md5.getMD5())) {
				// add retry here if necessary
				throw new BlobStoreCommunicationException("S3 upload corrupted, MD5s don't match");
			}
		} catch (SdkException e) {
			throw new BlobStoreCommunicationException(
					"Error stating S3 object: " + e.getMessage(), e);
		}
		try {
			//possible that this was inserted just prior to saving the object
			//so do update vs. insert since the data must be the same
			col.updateOne(
					new Document(Fields.S3_CHKSUM, md5.getMD5()),
					new Document("$set", new Document(Fields.S3_KEY, key)
							.append(Fields.S3_SORTED, sorted)),
					new UpdateOptions().upsert(true));
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", me);
		}
	}
	
	private String toS3Key(final UUID uuid) {
		final String m = uuid.toString();
		return m.substring(0, 2) + "/" + m.substring(2, 4) + "/" + m.substring(4, 6) + "/" + m;
	}
	
	private Document getBlobEntry(final MD5 md5)
			throws BlobStoreCommunicationException, NoSuchBlobException {
		try {
			final Document ret = col.find(new Document(Fields.S3_CHKSUM, md5.getMD5())).first();
			if (ret == null) {
				throw new NoSuchBlobException("No blob saved with chksum " + md5.getMD5(), md5);
			}
			return ret;
		} catch (MongoException me) {
			throw new BlobStoreCommunicationException(
					"Could not read from the mongo database", me);
		}
	}

	@Override
	public ByteArrayFileCache getBlob(final MD5 md5, final ByteArrayFileCacheManager bafcMan)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException,
				NoSuchBlobException, IOException {
		requireNonNull(bafcMan, "bafcMan");
		final Document entry = getBlobEntry(requireNonNull(md5, "md5"));
		final boolean sorted = entry.getBoolean(Fields.S3_SORTED);
		final String key = entry.getString(Fields.S3_KEY);
		try (final ResponseInputStream<GetObjectResponse> obj = s3.getClient().getObject(
				GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			return bafcMan.createBAFC(obj, true, sorted);
		} catch (NoSuchKeyException e) {
			throw new BlobStoreCommunicationException(
					"Inconsistent MongoDB and S3 records for MD5 " + md5.getMD5(), e);
		} catch (SdkException e) {
			throw new BlobStoreCommunicationException(
					"Error getting S3 object: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new BlobStoreCommunicationException("IO Error accessing blob: " +
					e.getMessage(), e);
		}
	}

	@Override
	public void removeBlob(final MD5 md5)
			throws BlobStoreAuthorizationException, BlobStoreCommunicationException {
		final Document entry;
		try {
			entry = getBlobEntry(requireNonNull(md5, "md5"));
		} catch (NoSuchBlobException nb) {
			return; //already gone
		}
		try {
			col.deleteOne(new Document(Fields.S3_CHKSUM, md5.getMD5()));
		} catch (MongoException e) {
			throw new BlobStoreCommunicationException(
					"Could not write to the mongo database", e);
		}
		try {
			// could race here, but just means a 1 time exception and then everything's fine
			// don't worry about it for now
			s3.getClient().deleteObject(DeleteObjectRequest.builder()
					.bucket(bucket)
					.key((String)entry.get(Fields.S3_KEY))
					.build());
		} catch (SdkException e) {
			throw new BlobStoreCommunicationException("Failed to delete blob: " +
					e.getMessage(), e);
		}
	}

	@Override
	public List<DependencyStatus> status() {
		try {
			s3.getClient().headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		} catch (SdkException e) {
			LoggerFactory.getLogger(getClass()).error("Failed to connect to S3", e);
			return Arrays.asList(new DependencyStatus(
					false, "Failed to connect to S3: " + e.getMessage(), "S3", "Unknown"));
		}
		return Arrays.asList(new DependencyStatus(true, "OK", "S3", "Unknown"));
	}

}
