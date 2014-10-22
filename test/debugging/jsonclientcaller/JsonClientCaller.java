package debugging.jsonclientcaller;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenExpiredException;

import java.net.*;
import java.nio.charset.Charset;
import java.io.*;
//import java.security.KeyManagementException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
//import java.security.cert.X509Certificate;
import java.util.*;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper class used in client side of java generated code for JSON RPC calling.
 * @author rsutormin
 */
public class JsonClientCaller {

	public final URL serviceUrl;
	private ObjectMapper mapper;
	@SuppressWarnings("unused")
	private String user = null;
	private char[] password = null;
	private AuthToken accessToken = null;
	private boolean allowInsecureHttp = false;
	private boolean trustAllCerts = false;
	@SuppressWarnings("unused")
	private Integer connectionReadTimeOut = 30 * 60 * 1000;
	private File fileForNextRpcResponse = null;
	
//	private static TrustManager[] GULLIBLE_TRUST_MGR = new TrustManager[] {
//		new X509TrustManager() {
//			public X509Certificate[] getAcceptedIssuers() {
//				return new X509Certificate[0];
//			}
//			public void checkClientTrusted(X509Certificate[] certs,
//					String authType) {}
//			public void checkServerTrusted(X509Certificate[] certs,
//					String authType) {}
//		}
//	};
//		
//	private static HostnameVerifier GULLIBLE_HOSTNAME_VERIFIER =
//		new HostnameVerifier() {
//			public boolean verify(String hostname, SSLSession session) {
//				return true;
//			}
//	};
	
	
	public JsonClientCaller(URL url) {
		serviceUrl = url;
		mapper = new ObjectMapper(); //.registerModule(new JacksonTupleModule());
	}

	public JsonClientCaller(URL url, AuthToken accessToken) throws UnauthorizedException, IOException {
		this(url);
		this.accessToken = accessToken;
		try {
			AuthService.validateToken(accessToken);
		} catch (TokenExpiredException ex) {
			throw new UnauthorizedException("Token validation failed", ex);
		}
	}

	public JsonClientCaller(URL url, String user, String password) throws UnauthorizedException, IOException {
		this(url);
		this.user = user;
		this.password = password.toCharArray();
		accessToken = requestTokenFromKBase(user, this.password);
	}
	
	/** Determine whether this client allows insecure http connections
	 * (vs. https).
	 * @return true if insecure connections are allowed.
	 */
	public boolean isInsecureHttpConnectionAllowed() {
		return allowInsecureHttp;
	}

	/** Deprecated - use isInsecureHttpConnectionAllowed().
	 * @deprecated
	 * @return
	 */
	public boolean isAuthAllowedForHttp() {
		return allowInsecureHttp;
	}

	/** Allow insecure http connections (vs. https). In production the value
	 * should always be false.
	 * @param allowed - true to allow insecure connections.
	 */
	public void setInsecureHttpConnectionAllowed(final boolean allowed) {
		allowInsecureHttp = allowed;
	}
	
	/** Deprecated - use setInsecureHttpConnectionAllowed
	 * @param isAuthAllowedForHttp
	 * @deprecated
	 */
	public void setAuthAllowedForHttp(boolean isAuthAllowedForHttp) {
		this.allowInsecureHttp = isAuthAllowedForHttp;
	}
	
	/** Trust all SSL certificates. By default, self-signed certificates
	 * may not be trusted and an error will occur when attempting to
	 * connect to such a server. 
	 * In production the value should always be false.
	 * 
	 * @param trustAll true to trust all SSL certificates.
	 */
	public void setAllSSLCertificatesTrusted(final boolean trustAll) {
		trustAllCerts = trustAll;
	}
	
	/** Determine whether this client trusts all SSL Certificates.
	 * @return true if this client trusts all SSL Certificates.
	 */
	public boolean isAllSSLCertificatesTrusted() {
		return trustAllCerts;
	}
	
	public void setConnectionReadTimeOut(Integer connectionReadTimeOut) {
		this.connectionReadTimeOut = connectionReadTimeOut;
	}

	private HttpURLConnection setupCall(boolean authRequired) throws IOException, JsonClientException {
		HttpURLConnection conn = (HttpURLConnection) serviceUrl.openConnection();
//		conn.setConnectTimeout(10000);
//		if (connectionReadTimeOut != null) {
//			conn.setReadTimeout(connectionReadTimeOut);
//		}
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
//		if (authRequired || accessToken != null) {
//			if (!(conn instanceof HttpsURLConnection || allowInsecureHttp)) {
//				throw new UnauthorizedException("RPC method required authentication shouldn't " +
//						"be called through unsecured http, use https instead or call " +
//						"setAuthAllowedForHttp(true) for your client");
//			}
//			if (accessToken == null || accessToken.isExpired()) {
//				if (user == null) {
//					if (accessToken == null) {
//						throw new UnauthorizedException("RPC method requires authentication but neither " +
//								"user nor token was set");
//					} else {
//						throw new UnauthorizedException("Token is expired and can not be reloaded " +
//								"because user wasn't set");
//					}
//				}
//				accessToken = requestTokenFromKBase(user, password);
//			}
			conn.setRequestProperty("Authorization", accessToken.toString());
//		}
//		if (conn instanceof HttpsURLConnection && trustAllCerts) {
//			final HttpsURLConnection hc = (HttpsURLConnection) conn;
//			final SSLContext sc;
//			try {
//				sc = SSLContext.getInstance("SSL");
//			} catch (NoSuchAlgorithmException e) {
//				throw new RuntimeException(
//						"Couldn't get SSLContext instance", e);
//			}
//			try {
//				sc.init(null, GULLIBLE_TRUST_MGR, new SecureRandom());
//			} catch (KeyManagementException e) {
//				throw new RuntimeException(
//						"Couldn't initialize SSLContext", e);
//			}
//			hc.setSSLSocketFactory(sc.getSocketFactory());
//			hc.setHostnameVerifier(GULLIBLE_HOSTNAME_VERIFIER);
//		}
		return conn;
	}
	
	public static AuthToken requestTokenFromKBase(String user, char[] password)
			throws UnauthorizedException, IOException {
		try {
			return AuthService.login(user, new String(password)).getToken();
		} catch (AuthException ex) {
			throw new UnauthorizedException("Could not authenticate user", ex);
		}
	}
	
	public static String streamToString(InputStream os) {
		Scanner s = new Scanner(os);
		s.useDelimiter("\\A");
		String ret = s.next();
		s.close();
		return ret;
	}
		
	public <ARG, RET> RET jsonrpcCall(String method, ARG arg,
			TypeReference<RET> cls, boolean ret, boolean authRequired)
			throws IOException, JsonClientException {
		HttpURLConnection conn = setupCall(authRequired);
//		String id = ("" + Math.random()).replace(".", "");
		String id = "12345";
		// Calculate content-length before
		final long[] sizeWrapper = new long[] {0};
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b) {sizeWrapper[0]++;}
			@Override
			public void write(byte[] b) {sizeWrapper[0] += b.length;}
			@Override
			public void write(byte[] b, int o, int l) {sizeWrapper[0] += l;}
		};
		writeRequestData(method, arg, os, id);
		// Set content-length
		conn.setFixedLengthStreamingMode(sizeWrapper[0]);
		// Write real data into http output stream
		writeRequestData(method, arg, conn.getOutputStream(), id);
		// Read response
		int code = conn.getResponseCode();
		conn.getResponseMessage();
		InputStream istream;
		if (code == 500) {
			istream = conn.getErrorStream();
		} else {
			istream = conn.getInputStream();
		}
		// Parse response into json
		String r = streamToString(istream);
		System.out.println(r);
		if (true) {return null;}
		@SuppressWarnings("unused")
		UnclosableInputStream wrapStream = new UnclosableInputStream(istream);
		if (fileForNextRpcResponse == null) {
			// *** returning here is always successful ***
			
			JsonParser jp = mapper.getFactory().createParser(wrapStream);
			// ** returning here always fails
			if (true) { return null; } //allow compile
			try {
				checkToken(JsonToken.START_OBJECT, jp.nextToken());
			} catch (JsonParseException ex) {
				String receivedHeadingMessage = wrapStream.getHeadingBuffer();
				if (receivedHeadingMessage.startsWith("{"))
					throw ex;
				throw new JsonClientException("Server response is not in JSON format:\n" + 
						receivedHeadingMessage);
			}
			Map<String, String> retError = null;
			RET res = null;
			while (jp.nextToken() != JsonToken.END_OBJECT) {
				checkToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
				String fieldName = jp.getCurrentName();
				if (fieldName.equals("error")) {
					jp.nextToken();
					retError = jp.getCodec().readValue(jp, new TypeReference<Map<String, String>>(){});
				} else if (fieldName.equals("result")) {
					checkFor500(code, wrapStream);
					jp.nextToken();
					try {
						res = jp.getCodec().readValue(jp, cls);
					} catch (JsonParseException e) {
						throw new JsonClientException(
								"Parse error while parsing response in: " +
								wrapStream.getHeadingBuffer(), e);
					}
				} else {
					jp.nextToken();
					jp.getCodec().readValue(jp, Object.class);
				}
			}
			if (retError != null) {
				String data = retError.get("data") == null ? retError.get("error") : retError.get("data");
				throw new ServerException(retError.get("message"),
						new Integer(retError.get("code")), retError.get("name"),
						data);
			}
			if (res == null && ret)
				throw new ServerException("An unknown server error occured", 0, "Unknown", null);
			return res;
		} else {
//			FileOutputStream fos = null;
//			try {
//				fos = new FileOutputStream(fileForNextRpcResponse);
//				byte[] rpcBuffer = new byte[10000];
//				while (true) {
//					int count = wrapStream.read(rpcBuffer);
//					if (count < 0)
//						break;
//					fos.write(rpcBuffer, 0, count);
//				}
//				fos.close();
//				JsonTokenStream jts = new JsonTokenStream(fileForNextRpcResponse);
//				Map<String, UObject> resp;
//				try {
//					resp = mapper.readValue(jts, new TypeReference<Map<String, UObject>>() {});
//				} catch (JsonParseException ex) {
//					String receivedHeadingMessage = wrapStream.getHeadingBuffer();
//					if (receivedHeadingMessage.startsWith("{"))
//						throw ex;
//					throw new JsonClientException("Server response is not in JSON format:\n" + 
//							receivedHeadingMessage);
//				} finally {
//					jts.close();
//				}
//				if (resp.containsKey("error")) {
//					Map<String, String> retError = resp.get("error").asClassInstance(new TypeReference<Map<String, String>>(){});
//					String data = retError.get("data") == null ? retError.get("error") : retError.get("data");
//					throw new ServerException(retError.get("message"),
//							new Integer(retError.get("code")), retError.get("name"),
//							data);
//				} if (resp.containsKey("result")) {
//					checkFor500(code, wrapStream);
//					RET res = mapper.readValue(resp.get("result").getPlacedStream(), cls);
//					return res;
//				} else {
//					throw new ServerException("An unknown server error occured", 0, "Unknown", null);
//				}
//			} finally {
//				fileForNextRpcResponse = null;
//				if (fos != null)
//					try {
//						fos.close();
//					} catch (Exception ignore) {}
//			}
			wrapStream.close();
			return null;
		}
	}

	private static void checkFor500(int code, UnclosableInputStream wrapStream)
			throws IOException, JsonClientException {
		if (code == 500) {
			String header = wrapStream.getHeadingBuffer();
			if (header.length() > 300)
				header = header.substring(0, 300) + "...";
			throw new JsonClientException("Server response contains result but has error code 500, " +
					"response header is:\n" + header);
		}
	}

	private static void checkToken(JsonToken expected, JsonToken actual) throws JsonClientException {
		if (expected != actual)
			throw new JsonClientException("Expected " + expected + " token but " + actual + " was occured");
	}
		
	public void writeRequestData(String method, Object arg, OutputStream os, String id) 
			throws IOException {
		JsonGenerator g = mapper.getFactory().createGenerator(os, JsonEncoding.UTF8);
		g.writeStartObject();
		g.writeObjectField("params", arg);
		g.writeStringField("method", method);
		g.writeStringField("version", "1.1");
		g.writeStringField("id", id);
		g.writeEndObject();
		g.close();
		os.flush();
	}
	
	public void setFileForNextRpcResponse(File f) {
		this.fileForNextRpcResponse = f;
	}
	
	public AuthToken getToken() {
		return accessToken;
	}
	
	public URL getURL() {
		return serviceUrl;
	}
	
	private static class UnclosableInputStream extends InputStream {
		private InputStream inner;
		private boolean isClosed = false;
		private ByteArrayOutputStream headingBuffer = new ByteArrayOutputStream();
		
		public UnclosableInputStream(InputStream inner) {
			this.inner = inner;
		}
		
		private boolean isHeadingBufferFull() {
			return headingBuffer.size() > 10000;
		}
		
		public String getHeadingBuffer() throws IOException {
			while ((!isClosed) && (!isHeadingBufferFull()))
				read();
			return new String(headingBuffer.toByteArray(), Charset.forName("UTF-8"));
		}
		
		@Override
		public int read() throws IOException {
			if (isClosed)
				return -1;
			int ret = inner.read();
			if (ret < 0) {
				isClosed = true;
			} else if (!isHeadingBufferFull()) {
				headingBuffer.write(ret);
			}
			return ret;
		}
		
		@Override
		public int available() throws IOException {
			if (isClosed)
				return 0;
			return inner.available();
		}
		
		@Override
		public void close() throws IOException {
			isClosed = true;
		}
		
		@Override
		public synchronized void mark(int readlimit) {
			inner.mark(readlimit);
		}
		
		@Override
		public boolean markSupported() {
			return inner.markSupported();
		}
		
		@Override
		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (isClosed)
				return -1;
			int realLen = inner.read(b, off, len);
			if (realLen < 0) {
				isClosed = true;
			} else if (realLen > 0 && !isHeadingBufferFull()) {
				headingBuffer.write(b, off, realLen);
			}
			return realLen;
		}
		
		@Override
		public synchronized void reset() throws IOException {
			if (isClosed)
				return;
			inner.reset();
		}
		
		@Override
		public long skip(long n) throws IOException {
			if (isClosed)
				return 0;
			return inner.skip(n);
		}
	}
}
