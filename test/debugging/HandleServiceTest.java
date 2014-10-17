package debugging;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;

import us.kbase.abstracthandle.AbstractHandleClient;


public class HandleServiceTest {
	private static final String EXPC_ERR = 
			"{\"version\":\"1.1\",\"error\":{\"name\":\"JSONRPCError\",\"code\":-32300,\"message\":\"HTTP GET not allowed.\"},\"id\":";
	private static final String URL = "http://dev03.berkeley.kbase.us:7109";

	public static void main(String [] args) throws Exception{
		System.out.println(URL);
		int count = Integer.parseInt(args[2]);
		for (int i = 0; i < count; i++) {
			System.out.print(i + "\n");
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
			try {
				final AbstractHandleClient hmc;
				hmc = new AbstractHandleClient(new URL(URL), args[0], args[1]);
				hmc.setIsInsecureHttpConnectionAllowed(true);
				hmc.setAllSSLCertificatesTrusted(true);
				hmc.areReadable(Arrays.asList("KBH_3"));
			} catch (Exception e) {
				System.out.println(e.getClass().getName() + ": " +
						e.getLocalizedMessage());
			}
			Thread.sleep(1000);
		}
	}
}
