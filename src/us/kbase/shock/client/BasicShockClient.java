package us.kbase.shock.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;

public class BasicShockClient {
	
	private final URI baseurl;
	private final URI nodeurl;
	private final HttpClient client = new DefaultHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private final AuthToken token;
	
	private final String AUTH = "Authorization";
	private final String OAUTH = "OAuth ";
	private final String DOWNLOAD = "/?download";
	private final String ATTRIBFILE = "attribs";
	
	
	public BasicShockClient(URL url) throws IOException, 
			InvalidShockUrlException {
		this(url, null);
	}
	
	@SuppressWarnings("unchecked")
	public BasicShockClient(URL url, AuthToken token) throws IOException, 
			InvalidShockUrlException{

		this.token = token;
//		
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
	
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	@SuppressWarnings("unchecked")
	public ShockNode getNode(ShockNodeId id) throws IOException {
		//TODO check for 200, if not then what?
		//TODO check for errors in general
		final URI targeturl = nodeurl.resolve(id.toString());
		final HttpGet htg = new HttpGet(targeturl);
		if (token != null) {
			htg.setHeader(AUTH, OAUTH + token);
		}
		final HttpResponse response = client.execute(htg);
		//TODO if 401 unauth'd
		final String resp = EntityUtils.toString(response.getEntity());
		Map<String, Object> shockresp;
		try {
			shockresp = mapper.readValue(resp, Map.class);
		} catch (JsonParseException jpe) {
			//TODO throw better error
			throw new Error(jpe); //something's broken
		}
		System.out.println(shockresp);
		//TODO shocknode class		
		return new ShockNode();
	}
	
	public String getFileAsString(ShockNodeId id) throws IOException {
		//TODO deal with duplicate code
		//TODO check for 200? if not then what?
		//TODO check for errors in general
		final URI targeturl = nodeurl.resolve(id.toString() + DOWNLOAD);
		final HttpGet htg = new HttpGet(targeturl);
		if (token != null) {
			htg.setHeader(AUTH, OAUTH + token);
		}
		final HttpResponse response = client.execute(htg);
		//TODO if 401 unauth'd
		//TODO if >299 parse json and return error
		System.out.println(response);
		return EntityUtils.toString(response.getEntity());
	}
	
	public ShockNode addNode(String attributes, String file, String filename)
			throws IOException {
		//TODO attributes as object
		//TODO duplicate code
		final HttpPost htp = new HttpPost(nodeurl);
		if (token != null) {
			htp.setHeader(AUTH, OAUTH + token);
		}
//		ByteArrayBody bab = new ByteArrayBody(file.getBytes(), filename);
		final MultipartEntity mpe = new MultipartEntity();
		mpe.addPart("upload", new ByteArrayBody(file.getBytes(), filename));
		mpe.addPart("attributes", new ByteArrayBody(attributes.getBytes(), 
				ATTRIBFILE));
		htp.setEntity(mpe);
		HttpResponse response = client.execute(htp);
		System.out.println(response);
		System.out.println(EntityUtils.toString(response.getEntity()));
		return new ShockNode();
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
		System.out.println(bsc.getShockUrl());
		ShockNodeId snid = new ShockNodeId("9c733533-be52-4592-b730-d426d1b51f2a");
		bsc.getNode(snid);
		System.out.println(bsc.getFileAsString(snid));
		
		BasicShockClient bsc2 = new BasicShockClient(new URL("http://kbase.us/services/shock-api"));
		bsc2.getNode(new ShockNodeId("9ae2658e-057f-4f89-81a1-a41c09c7313a"));
		
		bsc.addNode("{\"foo\": \"bar2\"}", "some serious crap right here", "seriouscrapfile");
		
	}

}
