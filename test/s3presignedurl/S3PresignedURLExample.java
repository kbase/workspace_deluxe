package s3presignedurl;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import us.kbase.common.test.TestCommon;

public class S3PresignedURLExample {
	
	// https://github.com/aws/aws-sdk-java-v2/issues/849
	
	public static void main(String[] args)
			throws URISyntaxException, MalformedURLException, IOException {
		final String s3key = args[0];
		final String s3secret = args[1];
		final String filePath = args[2];
		final String region = "us-west-1";
		// has to be prefixed by http or things go squirrelly with a opaque exception.
		final URI host = new URI("http://localhost:9000"); // minio
		final String bucket = "foo";
		final String objectName = "obj1";
		
		TestCommon.stfuLoggers();
		final AwsBasicCredentials creds = AwsBasicCredentials.create(s3key, s3secret);
		final S3Client cli = S3Client.builder()
				.region(Region.of(region))
				.endpointOverride(host)
				.credentialsProvider(StaticCredentialsProvider.create(creds))
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.httpClient(UrlConnectionHttpClient.create())
				// Don't need to disable ssl
				.build();
		try {
			cli.createBucket(CreateBucketRequest.builder()
					.bucket(bucket)
					.build());
		} catch (BucketAlreadyOwnedByYouException e) {
			// do nothing, we're fat dumb and happy
		}

		// no way to get config from client so this will need to be encapsulated for mocking
		// purposes
		final Aws4PresignerParams params = Aws4PresignerParams.builder()
				.expirationTime(Instant.ofEpochSecond(15 * 60))
				.awsCredentials(creds)
				.signingName("s3")
				.signingRegion(Region.of(region))
				.build();
		final SdkHttpFullRequest request = SdkHttpFullRequest.builder()
				.encodedPath("/" + bucket + "/" + objectName)
//				.encodedPath("/" + bucket + "a" + "/" + objectName)
				.host(host.getHost())
				.port(host.getPort())
				.method(SdkHttpMethod.PUT)
				.protocol(host.getScheme())
				.build();
		final SdkHttpFullRequest result = AwsS3V4Signer.create().presign(request, params);
		final URI target = result.getUri();
		System.out.println(target);
		
		final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(1000); //perhaps these should be configurable
		cm.setDefaultMaxPerRoute(1000);
		//set timeouts for the client for 1/2m for conn req timeout and std timeout
		final CloseableHttpClient client = HttpClients.custom().setConnectionManager(cm)
				.build();
		
		try (final InputStream is = Files.newInputStream(Paths.get(filePath))) {
			final HttpPut htp = new HttpPut(target);
			final BasicHttpEntity ent = new BasicHttpEntity();
			ent.setContent(new BufferedInputStream(is));
			ent.setContentLength(Files.size(Paths.get(filePath)));
			htp.setEntity(ent);
			final CloseableHttpResponse res = client.execute(htp);
			if (res.getStatusLine().getStatusCode() > 399) {
				final byte[] buffer = new byte[1000];
				try (final InputStream in = res.getEntity().getContent()) {
					new DataInputStream(in).readFully(buffer);
				} catch (EOFException e) {
					// do nothing
				}
				throw new IllegalArgumentException(String.format(
						"Error saving file to S3 (%s), truncated response follows:\n%s",
						res.getStatusLine().getStatusCode(),
						new String(buffer, StandardCharsets.UTF_8).trim()));
			}
		}
		
		/*
		final HttpURLConnection conn = (HttpURLConnection) target.toURL().openConnection();
		try {
			conn.setRequestMethod("PUT");
			conn.setFixedLengthStreamingMode(Files.size(Paths.get(filePath)));
			conn.setDoOutput(true);
			conn.setDoInput(true);
			
			try (final OutputStream os = conn.getOutputStream()) {
				// dealing with io errors here is a real pain
				try (final InputStream is = Files.newInputStream(Paths.get(filePath))) {
					try {
						IOUtils.copy(new BufferedInputStream(is), os);
					} catch (IOException e) {
						int code = conn.getResponseCode();
						final String err = getError(conn);
						if (!err.isEmpty()) {
							throw new IllegalArgumentException(String.format(
									"Error saving file to S3 (%s), truncated response follows:\n%s",
									code, err));
						} else {
							throw e;
						}
					}
				}
				
				final int code = conn.getResponseCode();
				if (code > 399) {
					throw new IllegalArgumentException(
							"Error saving file to S3, truncated response follows:\n " +
									getError(conn));
				}
			}
		} finally {
			if (conn.getErrorStream() != null) {
				conn.getErrorStream().close();
			}
		}
		*/
		final HeadObjectResponse objhead = cli.headObject(HeadObjectRequest.builder()
				.bucket(bucket)
				.key(objectName)
				.build());
		
		System.out.println(objhead);
	}
	/*
	private static String getError(final HttpURLConnection conn) throws IOException {
		if (conn.getErrorStream() == null) {
			return "";
		}
		final byte[] buffer = new byte[1000];
		try (final InputStream is = conn.getErrorStream()) {
			new DataInputStream(is).readFully(buffer);
		}
		return new String(buffer, StandardCharsets.UTF_8).trim();
	}
	*/
}
