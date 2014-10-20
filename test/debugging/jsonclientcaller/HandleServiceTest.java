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


public class HandleServiceTest {
	private static final String EXPC_ERR = 
			"{\"version\":\"1.1\",\"error\":{\"name\":\"JSONRPCError\",\"code\":-32300,\"message\":\"HTTP GET not allowed.\"},\"id\":";
	private static final String URL = "http://dev03.berkeley.kbase.us:7109";
	private static final boolean PRIOR_GET = false;
	private static final String TEST = "client"; // guts or client or caller
	private static final int SLEEP = 1000; //ms between requests
	

	public static void main(String [] args) throws Exception{
		System.out.println(URL);
		String user = args[0];
		String pwd = args[1];
		int count = Integer.parseInt(args[2]);
		int excepts = 0;
		for (int i = 0; i < count; i++) {
			System.out.print(i + "\n");
			if (PRIOR_GET) {
				HttpURLConnection httpConn = (HttpURLConnection)
						new URL(URL).openConnection();
				httpConn.getResponseCode();
				InputStream is = httpConn.getErrorStream();
				Scanner s = new Scanner(is);
				s.useDelimiter("\\A");
				String err = s.next();
				if (!err.startsWith(EXPC_ERR)) {
					System.out.println(err);
				}
				s.close();
			}
			try {
				if (TEST.equals("client")) {
					testClient(user, pwd);
				} else if (TEST.equals("guts")){
					testGuts(user, pwd);
				} else if (TEST.equals("caller")) {
					testCaller(user, pwd);
				} else {
					throw new Exception("no such test");
				}
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

	private static void testCaller(String user, String pwd)
			throws Exception {
		JsonClientCaller caller = new JsonClientCaller(new URL(URL), user, pwd);
		caller.setInsecureHttpConnectionAllowed(true);
		List<Object> args = new ArrayList<Object>();
		args.add(Arrays.asList("KBH_3"));
		TypeReference<List<Long>> retType = new TypeReference<List<Long>>() {};
		List<Long> res = caller.jsonrpcCall("AbstractHandle.are_readable", args, retType, true, true);
		System.out.println("res: " + res);
		
	}

	private static void testGuts(String user, String pwd) throws Exception {
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
	}

	private static void testClient(String user, String pwd)
			throws Exception {
		final AbstractHandleClient hmc;
		hmc = new AbstractHandleClient(new URL(URL),
				user, pwd);
		hmc.setIsInsecureHttpConnectionAllowed(true);
		hmc.setAllSSLCertificatesTrusted(true);
		hmc.areReadable(Arrays.asList("KBH_3"));
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
