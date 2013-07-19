package us.kbase.shock.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthToken;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;

public class BasicShockClient {
	
	private URI baseurl;
	private URI nodeurl;
	private HttpClient client = new DefaultHttpClient();
	private ObjectMapper mapper = new ObjectMapper();
	
	@SuppressWarnings("unchecked")
	public BasicShockClient(URL url) throws IOException, 
			InvalidShockUrlException {
		
		String turl = url.getProtocol() + "://" + url.getAuthority()
				+ url.getPath();
		if (turl.charAt(turl.length() - 1) != '/') {
			turl = turl + "/";
		}
		if (!(url.getProtocol().equals("http") ||
				url.getProtocol().equals("https"))) {
			throw new InvalidShockUrlException(turl.toString());
			
		}
		HttpResponse response = client.execute(new HttpGet(turl));
		String resp = EntityUtils.toString(response.getEntity());
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
//		if (url.getProtocol().equals("https")) {
//			shockurl = new URL("https", shockurl.getAuthority(),
//					shockurl.getPort(), shockurl.getFile());
//		}
		try {
			baseurl = shockurl.toURI();
		} catch (URISyntaxException use) {
			throw new Error(use); //something went badly wrong 
		}
		nodeurl = baseurl.resolve("node/");
	}
	
	public BasicShockClient(URL url, AuthToken token) {
		//TODO
	}
	
	public URL getShockUrl() {
		try {
			return baseurl.toURL();
		} catch (MalformedURLException mue) {
			throw new Error(mue); //something is seriously fuxxored
		}
	}
	
	public ShockNode getNode(ShockNodeId id) {
		
		return new ShockNode();
	}
	
	public static void main(String[] args) throws Exception {
		BasicShockClient bsc = new BasicShockClient(new URL("https://kbase.us/services/shock-ap"));
		System.out.println(bsc.getShockUrl());
		
		
	}

}
