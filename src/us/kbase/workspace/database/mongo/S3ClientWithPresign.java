package us.kbase.workspace.database.mongo;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import us.kbase.typedobj.core.Restreamable;

/** An S3 client that wraps the standard Amazon supplied S3 client and provides a method to
 * upload files using a presigned URL and standard http streaming.
 * 
 * See https://github.com/aws/aws-sdk-java-v2/issues/849
 * @author gaprice@lbl.gov
 *
 */
public class S3ClientWithPresign {
	
	// this isolates code that can't easily be mocked from the rest of the S3 blobstore.
	// all tests are in the S3BlobStore integration tests.
	
	private final S3Client client;
	private final S3Presigner presigner;
	private final CloseableHttpClient httpClient;
	
	/** Construct the client.
	 * @param host the host the client will interact with. Schema must be http or https.
	 * @param s3key the S3 access key.
	 * @param s3secret the S3 access secret.
	 * @param region the S3 region the client will contact.
	 * @throws URISyntaxException if the URL is not a valid URI.
	 */
	public S3ClientWithPresign(
			final URL host,
			final String s3key,
			final String s3secret,
			final Region region)
			throws URISyntaxException {
		final AwsCredentials creds = AwsBasicCredentials.create(
				checkString(s3key, "s3key"), checkString(s3secret, "s3secret"));
		this.presigner = S3Presigner.builder()
				.credentialsProvider(StaticCredentialsProvider.create(creds))
				.region(requireNonNull(region, "region"))
				.endpointOverride(requireNonNull(host, "host").toURI())
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
		// the client is not actually used in the code here, but might as well build and provide
		// it here, as all the info needed to build it is required for the presigner
		this.client = S3Client.builder()
				.region(region)
				.endpointOverride(host.toURI())
				.credentialsProvider(StaticCredentialsProvider.create(creds))
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.httpClient(UrlConnectionHttpClient.create())
				// Don't need to disable ssl
				.build();
		
		final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(1000); //perhaps these should be configurable
		cm.setDefaultMaxPerRoute(1000);
		// TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
		httpClient = HttpClients.custom().setConnectionManager(cm).build();
	}
	
	/** Get the standard S3 client.
	 * @return the S3 client.
	 */
	public S3Client getClient() {
		return client;
	}
	
	/** Load an object to S3 via a presigned url and standard HTTP streaming.
	 * The bucket and key are not checked for correctness prior to the upload attempt.
	 * @param bucket the bucket that will contain the object.
	 * @param key the object key.
	 * @param object the object data.
	 * @throws IOException if an error occurs.
	 */
	public void presignAndPutObject(
			final String bucket,
			final String key,
			final Restreamable object)
			throws IOException {
		checkString(key, "key");
		checkString(bucket, "bucket");
		requireNonNull(object, "object");
		// TODO CODE if this approach fixes the problem, pass in the PutObjectRequest instead
		// of the bucket and key
		// See https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html
		final PutObjectPresignRequest put = PutObjectPresignRequest.builder()
				.signatureDuration(Duration.ofMinutes(15))
				.putObjectRequest(
						PutObjectRequest.builder()
								.bucket(bucket)
								.key(key)
								.build()
				).build();
		final PresignedPutObjectRequest presignedPut = presigner.presignPutObject(put);
		
		final URL target = presignedPut.url();
		
		try (final InputStream is = object.getInputStream()) {
			final HttpPut htp;
			try {
				htp = new HttpPut(target.toURI());
			} catch (URISyntaxException e) {
				// this means the S3 SDK is generating urls that are invalid URIs, which is
				// pretty bizarre.
				// not sure how to test this.
				// since the URI contains credentials, we deliberately do not include the 
				// source error or URI
				throw new RuntimeException("S3 presigned request builder generated invalid URI");
			}
			final BasicHttpEntity ent = new BasicHttpEntity();
			ent.setContent(new BufferedInputStream(is));
			ent.setContentLength(object.getSize());
			htp.setEntity(ent);
			// error handling is a pain here. If the stream is large, for Minio (and probably most
			// other S3 instances) the connection dies. If the stream is pretty small,
			// you can get an error back.
			final CloseableHttpResponse res = httpClient.execute(htp);
			if (res.getStatusLine().getStatusCode() > 399) {
				final byte[] buffer = new byte[1000];
				try (final InputStream in = res.getEntity().getContent()) {
					new DataInputStream(in).readFully(buffer);
				} catch (EOFException e) {
					// do nothing
				}
				throw new IOException(String.format(
						"Error saving file to S3 (%s), truncated response follows:\n%s",
						res.getStatusLine().getStatusCode(),
						new String(buffer, StandardCharsets.UTF_8).trim()));
			}
		}
	}

}
