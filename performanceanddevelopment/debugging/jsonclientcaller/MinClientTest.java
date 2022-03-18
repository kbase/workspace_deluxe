package debugging.jsonclientcaller;

import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;


public class MinClientTest {

	private static final String URL = "http://localhost:10000";
	private static final int SLEEP = 000; //ms between requests
	private static final int COUNT = 6;

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
				conn.setFixedLengthStreamingMode(10);
				PrintWriter out = new PrintWriter(conn.getOutputStream());
				out.write("0123456789");
				out.close();
				System.out.print(conn.getResponseCode() + " ");
				System.out.println(conn.getResponseMessage());
				InputStream in = conn.getInputStream();
				System.out.println(streamToString(in));
				in.close();
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

	public static String streamToString(InputStream os) {
		Scanner s = new Scanner(os);
		s.useDelimiter("\\A");
		String ret = s.next();
		s.close();
		return ret;
	}
}
