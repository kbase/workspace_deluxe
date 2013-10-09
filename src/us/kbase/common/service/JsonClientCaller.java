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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonClientCaller {

	public final URL serviceUrl;
	private ObjectMapper mapper;
	private String user = null;
	private char[] password = null;
	private AuthToken accessToken = null;
	private boolean isAuthAllowedForHttp = false;
	
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
	
	private HttpURLConnection setupCall(boolean authRequired) throws IOException, JsonClientException {
		HttpURLConnection conn = (HttpURLConnection) serviceUrl.openConnection();
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
		OutputStream os = conn.getOutputStream();
		JsonGenerator g = mapper.getFactory().createGenerator(os, JsonEncoding.UTF8);

		g.writeStartObject();
		g.writeObjectField("params", arg);
		g.writeStringField("method", method);
		g.writeStringField("version", "1.1");
		String id = ("" + Math.random()).replace(".", "");
		g.writeStringField("id", id);
		g.writeEndObject();
		g.close();

		int code = conn.getResponseCode();
		conn.getResponseMessage();

		InputStream istream;
		if (code == 500) {
			istream = conn.getErrorStream();
		} else {
			istream = conn.getInputStream();
		}

		JsonNode node = mapper.readTree(new UnclosableInputStream(istream));
		if (node.has("error")) {
			Map<String, String> ret_error = mapper.readValue(mapper.treeAsTokens(node.get("error")), 
					new TypeReference<Map<String, String>>(){});
			
			String data = ret_error.get("data") == null ? ret_error.get("error") : ret_error.get("data");
			throw new ServerException(ret_error.get("message"),
					new Integer(ret_error.get("code")), ret_error.get("name"),
					data);
		}
		RET res = null;
		if (node.has("result"))
			res = mapper.readValue(mapper.treeAsTokens(node.get("result")), cls);
		if (res == null && ret)
			throw new ServerException("An unknown server error occured", 0, "Unknown", null);
		return res;
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
