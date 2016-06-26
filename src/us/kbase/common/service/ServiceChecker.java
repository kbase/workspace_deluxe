package us.kbase.common.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;


/** Methods for checking on KBase SDK services.
 * @author gaprice@lbl.gov
 *
 */
public class ServiceChecker {
	
	//TODO NOW tests
	
	public static final String PY_MSG = "No JSON object could be decoded";
	public static final int PY_CODE = -32700;
	public static final String JV_PL_MSG = "HTTP GET not allowed.";
	public static final int JV_PL_CODE = -32300;
	
	/** Checks whether the KBase SDK service at the given url is contactable.
	 * If not, throws an exception.
	 * @param serviceURL the url of the service.
	 * @throws ServiceException if the service cannot be contacted or is not a
	 * KBase SDK service.
	 */
	public static void checkService(final URL serviceURL)
			throws ServiceException {
		try {
			final HttpURLConnection conn =
					(HttpURLConnection) serviceURL.openConnection();
			conn.setRequestMethod("GET");
			conn.setDoOutput(true);
			conn.setUseCaches(false);

			final int responseCode = conn.getResponseCode();
			if (responseCode != 500) {
				handleNon500(serviceURL, conn, responseCode);
			} else {
				final String resp;
				try (final InputStream is = conn.getErrorStream()) {
					resp = IOUtils.toString(is);
				}
				conn.disconnect();
				final RpcResponse r = new ObjectMapper()
						.readValue(resp, RpcResponse.class);
				final RpcError err = r.error;
				//TODO TEST mock up a test service for the next two checks at some point
				if (err == null) {
					throw new ServiceException(String.format(
							"The service at %s is not a KBase SDK service: " +
							"no error field in RPC response as expected",
							serviceURL));
				}
				final String msg = err.message;
				final int code = err.code;
				if (!(PY_MSG.equals(msg) && PY_CODE == code) &&
						!(JV_PL_MSG.equals(msg) && JV_PL_CODE == code)) {
					throw new ServiceException(String.format(
							"The JSONRPC service at %s is not a KBase SDK " +
							"service. Code: %s, message: %s",
							serviceURL, code, msg));
				}
				// service is ok, nothing to do
			}
		} catch (IOException e) {
			throw new ServiceException(String.format(
					"Could not contact the service at URL %s: %s",
					serviceURL, e.getMessage()), e);
		}
	}

	private static void handleNon500(
			final URL serviceURL,
			final HttpURLConnection conn,
			final int responseCode)
			throws IOException, ServiceException {
		final String error;
		try (final InputStream es = conn.getInputStream()) {
			error = IOUtils.toString(es);
		}
		conn.disconnect();
		throw new ServiceException(String.format(
				"URL %s does not point to a KBase SDK generated " +
						"service. Code: %s, message: %s, content: %s",
						serviceURL, responseCode,
						conn.getResponseMessage(),
						error.substring(0, 1000)));
	}
	
	private static class RpcError {
		
		public String message;
		public int code;
		@SuppressWarnings("unused")
		public String name;
	}
	
	private static class RpcResponse {
		@SuppressWarnings("unused")
		public String version;
		public RpcError error;
	}
	
	@SuppressWarnings("serial")
	public static class ServiceException extends Exception {

		private ServiceException(String message, Throwable cause) {
			super(message, cause);
		}

		private ServiceException(String message) {
			super(message);
		}
	}
	
	public static void main(String[] args) throws Exception {
		checkService(new URL("http://the-internet.herokuapp.com/status_codes/500"));
	}
}
