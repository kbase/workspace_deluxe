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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.utils.AttributeMap;
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
			final Region region,
			final boolean trustAllCertificates)
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
				.httpClient(new DefaultSdkHttpClientBuilder().buildWithDefaults(
						AttributeMap.builder().put(
								SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
								trustAllCertificates)
							.build()))
				.build();
		
		httpClient = createHttpClient(trustAllCertificates);
	}
	
	private CloseableHttpClient createHttpClient(final boolean trustAllCertificates) {
		if (trustAllCertificates) {
			// http://stackoverflow.com/questions/19517538/ignoring-ssl-certificate-in-apache-httpclient-4-3
			final SSLConnectionSocketFactory sslsf;
			try {
				final SSLContextBuilder builder = new SSLContextBuilder();
				builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
				sslsf = new SSLConnectionSocketFactory(builder.build());
			} catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
				throw new RuntimeException("Unable to build http client", e);
			}

			final Registry<ConnectionSocketFactory> registry =
					RegistryBuilder.<ConnectionSocketFactory>create()
					.register("http", new PlainConnectionSocketFactory())
					.register("https", sslsf)
					.build();

			final PoolingHttpClientConnectionManager cm =
					new PoolingHttpClientConnectionManager(registry);
			cm.setMaxTotal(1000); //perhaps these should be configurable
			cm.setDefaultMaxPerRoute(1000);

			//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
			return HttpClients.custom()
					.setSSLSocketFactory(sslsf)
					.setConnectionManager(cm)
					.build();
		} else {
			final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
			cm.setMaxTotal(1000); //perhaps these should be configurable
			cm.setDefaultMaxPerRoute(1000);
			//TODO set timeouts for the client for 1/2m for conn req timeout and std timeout
			return HttpClients.custom().setConnectionManager(cm).build();
		}
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
			final PutObjectRequest put,
			final Restreamable object)
			throws IOException {
		requireNonNull(object, "object");
		// See https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html
		final PutObjectPresignRequest putreq = PutObjectPresignRequest.builder()
				// use a 2 hour timeout. Old Glassfish server scripts set timeout to 15m.
				// current tomcat server has a 30m default timeout.
				// most servers are going to have a 1h timeout at the most.
				.signatureDuration(Duration.ofHours(2))
				.putObjectRequest(requireNonNull(put, "put"))
				.build();
		final PresignedPutObjectRequest presignedPut = presigner.presignPutObject(putreq);
		
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
			try (final CloseableHttpResponse res = httpClient.execute(htp)) {
				// see https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html#API_PutObject_ResponseSyntax
				// only 200 is success, so don't count 3XX or any other 2XX as successful.
				// Maybe a bit conservative, but missing a fail or redirect here = corrupt WS data
				if (res.getStatusLine().getStatusCode() != 200) {
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

}
