package us.kbase.workspace.test.controllers.minio;

import static us.kbase.common.test.controllers.ControllerCommon.checkExe;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;

/** Q&D Utility to run a Minio server for the purposes of testing from
 * Java. Always run with the --compat flag.
 * @author gaprice@lbl.gov
 *
 */
public class MinioController {
	
	private final Path tempDir;
	
	private final Process minio;
	private final int port;

	public MinioController(
			final String minioExe,
			final String s3AccessKey,
			final String s3AccessSecret,
			final String region,
			final Path rootTempDir)
			throws Exception {
		tempDir = makeTempDirs(rootTempDir, "MinioController-", Arrays.asList("data"));
		port = findFreePort();
		
		checkExe(minioExe, "minio server");
		
		ProcessBuilder servpb = new ProcessBuilder(
				minioExe,
				"server",
				"--compat",
				"--address",
				"localhost:" + port,
				tempDir.resolve("data").toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("minio_server.log").toFile());
		
		servpb.environment().put("MINIO_ACCESS_KEY", s3AccessKey);
		servpb.environment().put("MINIO_SECRET_KEY", s3AccessSecret);
		minio = servpb.start();
		Thread.sleep(1000); //wait for server to start
	}
	
	
	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy(boolean deleteTempFiles) throws IOException {
		if (minio != null) {
			minio.destroy();
		}
		if (tempDir != null && deleteTempFiles) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}

	public static void main(String[] args) throws Exception {
		MinioController ac = new MinioController(
				"Minio",
				"foobar",
				"Wheewhoowhump",
				"us-west-1",
				Paths.get("minio_temp_dir"));
		System.out.println(ac.getServerPort());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy(false);
		reader.close();
	}
	
}
