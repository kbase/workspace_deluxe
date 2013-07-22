package us.kbase.shock.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.shock.client.exceptions.ExpiredTokenException;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;

public class BasicShockClient {
	
	private final URI baseurl;
	private final URI nodeurl;
	private final HttpClient client = new DefaultHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private AuthToken token;
	
	private static final String AUTH = "Authorization";
	private static final String OAUTH = "OAuth ";
	private static final String DOWNLOAD = "/?download";
	private static final String ATTRIBFILE = "attribs";
	private static final String ACL_READ = "/acl/read/";
	
	public BasicShockClient(URL url) throws IOException, 
			InvalidShockUrlException, ExpiredTokenException {
		this(url, null);
	}
	
	@SuppressWarnings("unchecked")
	public BasicShockClient(URL url, AuthToken token) throws IOException, 
			InvalidShockUrlException, ExpiredTokenException {

		updateToken(token);

		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		
		String turl = url.getProtocol() + "://" + url.getAuthority()
				+ url.getPath();
		if (turl.charAt(turl.length() - 1) != '/') {
			turl = turl + "/";
		}
		if (!(url.getProtocol().equals("http") ||
				url.getProtocol().equals("https"))) {
			throw new InvalidShockUrlException(turl.toString());
			
		}
		final HttpResponse response = client.execute(new HttpGet(turl));
		final String resp = EntityUtils.toString(response.getEntity());
		Map<String, Object> shockresp = null;
		try {
			shockresp = mapper.readValue(resp, Map.class);
		} catch (JsonParseException jpe) {
			throw new InvalidShockUrlException(turl.toString());
		}
		if (!shockresp.get("id").equals("Shock")) {
			throw new InvalidShockUrlException(turl.toString());
		}
		URL shockurl = new URL(shockresp.get("url").toString());
		//https->http is caused by the router, not shock, per Jared
		if (url.getProtocol().equals("https")) {
			shockurl = new URL("https", shockurl.getAuthority(),
					shockurl.getPort(), shockurl.getFile());
		}
		try {
			baseurl = shockurl.toURI();
		} catch (URISyntaxException use) {
			throw new Error(use); //something went badly wrong 
		}
		nodeurl = baseurl.resolve("node/");
	}
	
	public void updateToken(AuthToken token) throws ExpiredTokenException {
		if (token == null) {
			this.token = null;
			return;
		}
		if (token.isExpired()) {
			throw new ExpiredTokenException(token.getTokenId());
		}
		this.token = token;
	}
	
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	private ShockNode processShockNodeRequest(HttpRequestBase httpreq) throws
			IOException, ShockHttpException, ExpiredTokenException {
		authorize(httpreq);
		HttpResponse response = client.execute(httpreq);
		return getShockNode(response);
	}
	
	private ShockNode getShockNode(HttpResponse response) 
			throws IOException, ShockHttpException {
		final String resp = EntityUtils.toString(response.getEntity());
		try {
			return mapper.readValue(resp, ShockNodeResponse.class).getShockData();
		} catch (JsonParseException jpe) {
			throw new Error(jpe); //something's broken
		}
	}
	
	private void authorize(HttpRequestBase httpreq) throws
			ExpiredTokenException {
		if (token != null) {
			//TODO test when can get hands on expired token
			if (token.isExpired()) {
				throw new ExpiredTokenException(token.getTokenId());
			}
			httpreq.setHeader(AUTH, OAUTH + token);
		}
	}

	public ShockNode getNode(ShockNodeId id) throws IOException,
			ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpGet htg = new HttpGet(targeturl);
		return processShockNodeRequest(htg);
	}
	
	public String getFileAsString(ShockNodeId id) throws IOException,
			ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId() + DOWNLOAD);
		final HttpGet htg = new HttpGet(targeturl);
		authorize(htg);
		final HttpResponse response = client.execute(htg);
		final int code = response.getStatusLine().getStatusCode();
		if (code > 299) {
			getShockNode(response); //trigger errors
		}
		return EntityUtils.toString(response.getEntity());
	}
	
	public ShockNode addNode() throws IOException, ShockHttpException,
			JsonProcessingException, ExpiredTokenException {
		return _addNode(null, null, null);
	}
	
	public ShockNode addNode(Map<String, Object> attributes) throws
			IOException, ShockHttpException, JsonProcessingException,
			ExpiredTokenException {
		if (attributes == null) {
			throw new NullPointerException("attributes");
		}
		return _addNode(attributes, null, null);
	}
	
	public ShockNode addNode(String file, String filename) throws IOException,
			ShockHttpException, JsonProcessingException, ExpiredTokenException {
		if (file == null) {
			throw new NullPointerException("file");
		}
		if (filename == null) {
			throw new NullPointerException("filename");
		}
		return _addNode(null, file, filename);
	}
	
	public ShockNode addNode(Map<String, Object> attributes, String file,
			String filename) throws IOException, ShockHttpException,
			JsonProcessingException, ExpiredTokenException {
		if (attributes == null) {
			throw new NullPointerException("attributes");
		}
		if (file == null) {
			throw new NullPointerException("file");
		}
		if (filename == null) {
			throw new NullPointerException("filename");
		}
		return _addNode(attributes, file, filename);
	}
	
	private ShockNode _addNode(Map<String, Object> attributes, String file,
			String filename) throws IOException,
			ShockHttpException, JsonProcessingException, ExpiredTokenException {
		final HttpPost htp = new HttpPost(nodeurl);
		if (attributes != null && file != null) {
			final MultipartEntity mpe = new MultipartEntity();
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpe.addPart("attributes", new ByteArrayBody(attribs, ATTRIBFILE));
			}
			if (file != null) {
				mpe.addPart("upload", new ByteArrayBody(file.getBytes(), filename));
			}
			htp.setEntity(mpe);
		}
		return processShockNodeRequest(htp);
	}
	
	public void deleteNode(ShockNodeId id) throws IOException, 
			ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpDelete htd = new HttpDelete(targeturl);
		processShockNodeRequest(htd); //triggers throwing errors
	}
	
	public void setNodeReadable(ShockNodeId id, AuthUser user) throws 
		IOException, ShockHttpException,ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ + "?users=" + user.getEmail());
		final HttpPut htp = new HttpPut(targeturl);
		authorize(htp);
		final HttpResponse response = client.execute(htp);
		System.out.println(response);
		final String resp = EntityUtils.toString(response.getEntity());
		System.out.println(resp);
		ShockACL acls = null;
		try {
			acls = mapper.readValue(resp, ShockACLResponse.class).getShockData();
		} catch (JsonParseException jpe) {
			throw new Error(jpe); //something's broken
		}
		System.out.println(acls);
	}
	
	public void getACLs(ShockNode id) {
		//TODO
	}
	
	public void setNodeWorldReadable(ShockNode id) {
		//TODO
	}
	
	//for known good uris ONLY
	private URL uriToUrl(URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException mue) {
			throw new Error(mue); //something is seriously fuxxored
		}
	}
	
	public static void main(String[] args) throws Exception {
		AuthUser au = AuthService.login("x", "x");
//		System.out.println(au);
		BasicShockClient bsc = new BasicShockClient(new URL("http://localhost:7044"), au.getToken());
		System.out.println("***Add node");
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foo", "newbar");
		ShockNode node = bsc.addNode(attribs, "some serious crap right here", "seriouscrapfile");
		System.out.println(node);
		System.out.println("***Get node");
		System.out.println(bsc.getNode(node.getId()));
		System.out.println("***Get file");
		System.out.println(bsc.getFileAsString(node.getId()));
		System.out.println("***Get node with no auth");
		BasicShockClient bscNoAuth = new BasicShockClient(new URL("http://localhost:7044"));
		try {
			bscNoAuth.getNode(node.getId());
		} catch (ShockHttpException she) {
			System.out.println(she);
		}
		System.out.println("***delete node");
		bsc.deleteNode(node.getId());
		System.out.println("***get deleted node");
		try {
			System.out.println(bsc.getNode(node.getId()));
		} catch (ShockHttpException she) {
			System.out.println(she);
		}
		
		System.out.println("***Add empty node");
		ShockNode node2 = bsc.addNode();
		System.out.println("***Get non-existant file");
		try {
			bsc.getFileAsString(node2.getId());
		} catch (ShockHttpException she) {
			System.out.println(she);
		}
		ShockNode node2get = bsc.getNode(node2.getId());
		System.out.println(bsc.getNode(node2get.getId()));
		
		System.out.println("***set node readable***");
		AuthUser au2 = AuthService.login("kbasetest2", "@Suite525");
		bsc.setNodeReadable(node2get.getId(), au2);
		
		
//		System.out.println("***Test expired token***");
		//TODO that token wasn't expired. Pfft.
//		AuthToken expired = new AuthToken("");
//		try {
//			@SuppressWarnings("unused")
//			BasicShockClient bscbad = new BasicShockClient(new URL("http://fake.com"), expired);
//		} catch (ExpiredTokenException ete) {
//			System.out.println(ete);
//		}
//		try {
//			bsc.updateToken(expired);
//		} catch (ExpiredTokenException ete) {
//			System.out.println(ete);
//		}
		//TODO tests for tokens that expire while in the client
		
		BasicShockClient bsc2 = new BasicShockClient(new URL("http://kbase.us/services/shock-api"));
		ShockNodeId snid2 = new ShockNodeId("9ae2658e-057f-4f89-81a1-a41c09c7313a");
		System.out.println("***Get node " + snid2 + " from " + bsc2.getShockUrl());
		System.out.println(bsc2.getNode(snid2));
		
		//TODO test readable nodes
		//TODO test errors
		
		
	}

}
