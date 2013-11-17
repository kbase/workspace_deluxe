package us.kbase.common.service;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenExpiredException;

import java.net.*;
import java.io.*;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonClientCaller {

	public final URL serviceUrl;
	private ObjectMapper mapper;
	private String user = null;
	private char[] password = null;
	private AuthToken accessToken = null;
	private boolean isAuthAllowedForHttp = false;
	private Integer connectionReadTimeOut = 30 * 60 * 1000;
	
	public JsonClientCaller(URL url) {
		serviceUrl = url;
		mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
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

	public boolean isAuthAllowedForHttp() {
		return isAuthAllowedForHttp;
	}
	
	public void setAuthAllowedForHttp(boolean isAuthAllowedForHttp) {
		this.isAuthAllowedForHttp = isAuthAllowedForHttp;
	}
	
	public void setConnectionReadTimeOut(Integer connectionReadTimeOut) {
		this.connectionReadTimeOut = connectionReadTimeOut;
	}

	private HttpURLConnection setupCall(boolean authRequired) throws IOException, JsonClientException {
		HttpURLConnection conn = (HttpURLConnection) serviceUrl.openConnection();
		conn.setConnectTimeout(10000);
		if (connectionReadTimeOut != null) {
			conn.setReadTimeout(connectionReadTimeOut);
		}
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		if (authRequired || accessToken != null) {
			if (!(conn instanceof HttpsURLConnection || isAuthAllowedForHttp)) {
				throw new UnauthorizedException("RPC method required authentication shouldn't " +
						"be called through unsecured http, use https instead or call " +
						"setAuthAllowedForHttp(true) for your client");
			}
			if (accessToken == null || accessToken.isExpired()) {
				if (user == null) {
					if (accessToken == null) {
						throw new UnauthorizedException("RPC method requires authentication but neither " +
								"user nor token was set");
					} else {
						throw new UnauthorizedException("Token is expired and can not be reloaded " +
								"because user wasn't set");
					}
				}
				accessToken = requestTokenFromKBase(user, password);
			}
			conn.setRequestProperty("Authorization", accessToken.toString());
		}
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
		
	public <ARG, RET> RET jsonrpcCall(String method, ARG arg,
			TypeReference<RET> cls, boolean ret, boolean authRequired)
			throws IOException, JsonClientException {
		HttpURLConnection conn = setupCall(authRequired);
		String id = ("" + Math.random()).replace(".", "");
		// Calculate content-length before
		final int[] sizeWrapper = new int[] {0};
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
		JsonParser jp = mapper.getFactory().createParser(new UnclosableInputStream(istream));
		checkToken(JsonToken.START_OBJECT, jp.nextToken());
		Map<String, String> retError = null;
		RET res = null;
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			checkToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
			String fieldName = jp.getCurrentName();
			if (fieldName.equals("error")) {
				jp.nextToken();
				retError = jp.getCodec().readValue(jp, new TypeReference<Map<String, String>>(){});
			} else if (fieldName.equals("result")) {
				jp.nextToken();
				res = jp.getCodec().readValue(jp, cls);
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
	
	private static class UnclosableInputStream extends InputStream {
		private InputStream inner;
		private boolean isClosed = false;
		
		public UnclosableInputStream(InputStream inner) {
			this.inner = inner;
		}
		
		@Override
		public int read() throws IOException {
			if (isClosed)
				return -1;
			return inner.read();
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
			if (isClosed)
				return 0;
			return inner.read(b);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (isClosed)
				return 0;
			return inner.read(b, off, len);
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
