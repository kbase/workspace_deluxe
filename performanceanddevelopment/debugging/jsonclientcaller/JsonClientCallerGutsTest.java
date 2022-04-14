package debugging.jsonclientcaller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
//import java.util.Arrays;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;


public class JsonClientCallerGutsTest {

//	private static final String URL = "http://10.58.1.16:7057";
//	private static final String METHOD = "ProteinInfo.fids_to_domains";
//	private static final Object ARGS =
//			Arrays.asList(Arrays.asList("kb|g.3899.CDS.10"));
	
//	private static final String URL = "http://dev03.berkeley.kbase.us:7109";
//	private static final String METHOD = "AbstractHandle.list_handles";
	
//	private static final String URL = "http://localhost:7109";
//	private static final String METHOD = "AbstractHandle.list_handles";
	
	private static final String URL = "http://localhost:10000";
	private static final String METHOD = "PyLog.ver";
	
//	private static final String URL = "http://localhost/services/pl";
//	private static final String METHOD = "PyLog.ver";
	
	
//	private static final String URL = "http://localhost:20000";
//	private static final String METHOD = "Workspace.ver";
	
	private static final Object ARGS = new ArrayList<Object>();
	
	private static final int SLEEP = 000; //ms between requests
	private static final int COUNT = 10;
	

	public static void main(String [] args) throws Exception{
		System.out.println(URL);
		int excepts = 0;
		for (int i = 0; i < COUNT; i++) {
			System.out.print(i + "\n");
			try {
				HttpURLConnection conn =
						(HttpURLConnection) new URL(URL).openConnection();
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				final long[] sizeWrapper = new long[] {0};
				OutputStream os = new OutputStream() {
					@Override
					public void write(int b) {sizeWrapper[0]++;}
					@Override
					public void write(byte[] b) {sizeWrapper[0] += b.length;}
					@Override
					public void write(byte[] b, int o, int l) {sizeWrapper[0] += l;}
				};
				String id = "12345";
				
				writeRequestData(METHOD, ARGS, os, id);
				// Set content-length
				conn.setFixedLengthStreamingMode(sizeWrapper[0]);
//				conn.setChunkedStreamingMode(0);
				
				writeRequestData(METHOD, ARGS, conn.getOutputStream(), id);
				System.out.print(conn.getResponseCode() + " ");
				System.out.println(conn.getResponseMessage());
				InputStream istream = conn.getInputStream();
				// Parse response into json
				System.out.println(streamToString(istream));
				conn.disconnect();
			} catch (Exception e) {
				excepts++;
				System.out.println(e.getClass().getName() + ": " +
						e.getLocalizedMessage());
			}
			Thread.sleep(SLEEP);
		}
		System.out.println(String.format("Sleep: %s, failures %s/%s",
				SLEEP, excepts, COUNT));
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
	
	public static String streamToString(InputStream os) {
		Scanner s = new Scanner(os);
		s.useDelimiter("\\A");
		String ret = s.next();
		s.close();
		return ret;
	}
}
