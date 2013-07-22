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
	
	public BasicShockClient(URL url) throws IOException, 
			InvalidShockUrlException {
		this(url, null);
	}
	
	//TODO checkforexpiredtoken - need to add isExpired() method to AuthToken
	
	@SuppressWarnings("unchecked")
	public BasicShockClient(URL url, AuthToken token) throws IOException, 
			InvalidShockUrlException{

		this.token = token;
//		
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
	
	public void updateToken(AuthToken auth) {
		this.token = auth;
	}
	
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	public ShockNode getNode(ShockNodeId id) throws IOException,
			ShockHttpException {
		final URI targeturl = nodeurl.resolve(id.toString());
		final HttpGet htg = new HttpGet(targeturl);
		authorize(htg);
		final HttpResponse response = client.execute(htg);
		return getShockNode(response);
	}
	
	private ShockNode getShockNode(HttpResponse response) 
			throws IOException, ShockHttpException {
		final String resp = EntityUtils.toString(response.getEntity());
		try {
			return mapper.readValue(resp, ShockNodeResponse.class).getShockNode();
		} catch (JsonParseException jpe) {
			throw new Error(jpe); //something's broken
		}
	}
	
	private void authorize(HttpRequestBase httpreq) {
		//TODO check if token is expired, if so throw error
		if (token != null) {
			httpreq.setHeader(AUTH, OAUTH + token);
		}
	}
	
	public String getFileAsString(ShockNodeId id) throws IOException,
			ShockHttpException {
		final URI targeturl = nodeurl.resolve(id.toString() + DOWNLOAD);
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
			JsonProcessingException {
		return _addNode(null, null, null);
	}
	
	public ShockNode addNode(Map<String, Object> attributes) throws
			IOException, ShockHttpException, JsonProcessingException {
		if (attributes == null) {
			throw new NullPointerException("attributes");
		}
		return _addNode(attributes, null, null);
	}
	
	public ShockNode addNode(String file, String filename) throws IOException,
			ShockHttpException, JsonProcessingException {
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
			JsonProcessingException {
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
			ShockHttpException, JsonProcessingException {
		final HttpPost htp = new HttpPost(nodeurl);
		authorize(htp);
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
		HttpResponse response = client.execute(htp);
		ShockNode sn = getShockNode(response);
		return sn;
	}
	
	public void deleteNode(ShockNodeId id) throws IOException, 
			ShockHttpException {
		final URI targeturl = nodeurl.resolve(id.toString());
		final HttpDelete htd = new HttpDelete(targeturl);
		authorize(htd);
		final HttpResponse response = client.execute(htd);
		getShockNode(response); //triggers throwing errors
	}
	
	public void setNodeReadable(ShockNodeId id, AuthUser user) {
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
		
		BasicShockClient bsc2 = new BasicShockClient(new URL("http://kbase.us/services/shock-api"));
		ShockNodeId snid2 = new ShockNodeId("9ae2658e-057f-4f89-81a1-a41c09c7313a");
		System.out.println("***Get node " + snid2 + " from " + bsc2.getShockUrl());
		System.out.println(bsc2.getNode(snid2));
		
		//TODO test readable nodes
		//TODO test errors
		
		
	}

}
