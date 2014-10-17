package debugging;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;


public class HandleServiceTest {
	private static final String EXPC_ERR = 
			"{\"version\":\"1.1\",\"error\":{\"name\":\"JSONRPCError\",\"code\":-32300,\"message\":\"HTTP GET not allowed.\"},\"id\":";
	private static final String URL = "http://dev03.berkeley.kbase.us:7109";
	private static final boolean PRIOR_GET = false;
	private static final boolean CLIENT = false;
	

	public static void main(String [] args) throws Exception{
		System.out.println(URL);
		int count = Integer.parseInt(args[2]);
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
				if (CLIENT) {
					final AbstractHandleClient hmc;
					hmc = new AbstractHandleClient(new URL(URL),
							args[0], args[1]);
					hmc.setIsInsecureHttpConnectionAllowed(true);
					hmc.setAllSSLCertificatesTrusted(true);
					hmc.areReadable(Arrays.asList("KBH_3"));
				} else {
					HttpURLConnection conn =
							(HttpURLConnection) new URL(URL).openConnection();
					conn.setDoOutput(true);
					conn.setRequestMethod("POST");
					AuthToken accessToken = AuthService.login(
							args[0], args[1]).getToken();
					conn.setRequestProperty("Authorization",
							accessToken.toString());
					writeRequestData("AbstractHandle.are_readable",
							Arrays.asList(Arrays.asList("KBH_3")),
							conn.getOutputStream(), "12345");
					System.out.print(conn.getResponseCode() + " ");
					System.out.println(conn.getResponseMessage());
				}
			} catch (Exception e) {
				System.out.println(e.getClass().getName() + ": " +
						e.getLocalizedMessage());
			}
			Thread.sleep(1000);
		}
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
