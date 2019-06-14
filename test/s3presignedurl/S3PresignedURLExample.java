package s3presignedurl;

import java.net.URI;
import java.net.URISyntaxException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

public class S3PresignedURLExample {
	
	// https://github.com/aws/aws-sdk-java-v2/issues/849
	
	public static void main(String[] args) throws URISyntaxException {
		final String s3key = args[0];
		final String s3secret = args[1];
		final String region = "us-west-1";
		final String host = "http://localhost:9000"; // minio
		
		final S3Client cli = S3Client.builder()
				.region(Region.of(region))
				.endpointOverride(new URI(host))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(s3key, s3secret)))
				.serviceConfiguration(
						S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.httpClient(UrlConnectionHttpClient.create())
				// disable ssl
				.build();
		try {
			cli.createBucket(CreateBucketRequest.builder()
					.bucket("foo")
					.build());
		} catch (BucketAlreadyOwnedByYouException e) {
			// do nothing, we're fat dumb and happy
		}
	}
	

}
