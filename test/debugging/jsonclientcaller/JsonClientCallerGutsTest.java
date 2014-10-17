package debugging.jsonclientcaller;

import static debugging.jsonclientcaller.JsonClientCaller.streamToString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;


public class JsonClientCallerGutsTest {
	private static final String URL = "http://dev03.berkeley.kbase.us:7109";
	private static final int SLEEP = 1000; //ms between requests
	

	public static void main(String [] args) throws Exception{
		System.out.println(URL);
		String user = args[0];
		String pwd = args[1];
		int count = Integer.parseInt(args[2]);
		int excepts = 0;
		for (int i = 0; i < count; i++) {
			System.out.print(i + "\n");
			try {
				HttpURLConnection conn =
						(HttpURLConnection) new URL(URL).openConnection();
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				AuthToken accessToken = AuthService.login(
						user, pwd).getToken();
				conn.setRequestProperty("Authorization",
						accessToken.toString());
				final long[] sizeWrapper = new long[] {0};
				OutputStream os = new OutputStream() {
					@Override
					public void write(int b) {sizeWrapper[0]++;}
					@Override
					public void write(byte[] b) {sizeWrapper[0] += b.length;}
					@Override
					public void write(byte[] b, int o, int l) {sizeWrapper[0] += l;}
				};
				String method = "AbstractHandle.are_readable";
				Object arg = Arrays.asList(Arrays.asList("KBH_3"));
				String id = "12345";
				
				writeRequestData(method, arg, os, id);
				// Set content-length
				conn.setFixedLengthStreamingMode(sizeWrapper[0]);
				
				writeRequestData(method, arg, conn.getOutputStream(), id);
				System.out.print(conn.getResponseCode() + " ");
				System.out.println(conn.getResponseMessage());
				conn.getResponseMessage();
				InputStream istream;
				if (conn.getResponseCode() == 500) {
					istream = conn.getErrorStream();
				} else {
					istream = conn.getInputStream();
				}
				// Parse response into json
				System.out.println(streamToString(istream));
			} catch (Exception e) {
				excepts++;
				System.out.println(e.getClass().getName() + ": " +
						e.getLocalizedMessage());
			}
			Thread.sleep(SLEEP);
		}
		System.out.println(String.format("Sleep: %s, failures %s/%s",
				SLEEP, excepts, count));
	}

	public static void writeRequestData(String method, Object arg,
			OutputStream os, String id) 
			throws IOException {
		JsonGenerator g = new ObjectMapper().getFactory()
				.createGenerator(os, JsonEncoding.UTF8);
		g.writeStartObject();
		g.writeObjectField("params", arg);
		g.writeStringField("method", method);
		g.writeStringField("version", "1.1");
		g.writeStringField("id", id);
		g.writeEndObject();
		g.close();
		os.flush();
	}
}
