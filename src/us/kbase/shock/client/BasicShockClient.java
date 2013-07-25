package us.kbase.shock.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
	private static final ShockACLType ACL_READ = new ShockACLType("read");
	
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
		if (!shockresp.containsKey("id")) {
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
	
	private <T extends ShockResponse> ShockData
			processRequest(HttpRequestBase httpreq, Class<T> clazz) throws
			IOException, ShockHttpException, ExpiredTokenException {
		authorize(httpreq);
		HttpResponse response = client.execute(httpreq);
		return getShockData(response, clazz);
	}
	
	private <T extends ShockResponse> ShockData
			getShockData(HttpResponse response, Class<T> clazz) 
			throws IOException, ShockHttpException {
		final String resp = EntityUtils.toString(response.getEntity());
		try {
			return mapper.readValue(resp, clazz).getShockData();
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
		ShockNode sn = (ShockNode)processRequest(htg, ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	public byte[] getFile(ShockNodeId id) throws IOException,
			ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId() + DOWNLOAD);
		final HttpGet htg = new HttpGet(targeturl);
		authorize(htg);
		final HttpResponse response = client.execute(htg);
		final int code = response.getStatusLine().getStatusCode();
		if (code > 299) {
			getShockData(response, ShockNodeResponse.class); //trigger errors
		}
		return EntityUtils.toByteArray(response.getEntity());
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
	
	public ShockNode addNode(byte[] file, String filename) throws IOException,
			ShockHttpException, JsonProcessingException, ExpiredTokenException {
		if (file == null) {
			throw new NullPointerException("file");
		}
		if (filename == null) {
			throw new NullPointerException("filename");
		}
		return _addNode(null, file, filename);
	}
	
	public ShockNode addNode(Map<String, Object> attributes, byte[] file,
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
	
	private ShockNode _addNode(Map<String, Object> attributes, byte[] file,
			String filename) throws IOException, ShockHttpException,
			JsonProcessingException, ExpiredTokenException {
		final HttpPost htp = new HttpPost(nodeurl);
		if (attributes != null || file != null) {
			final MultipartEntity mpe = new MultipartEntity();
			if (attributes != null) {
				final byte[] attribs = mapper.writeValueAsBytes(attributes);
				mpe.addPart("attributes", new ByteArrayBody(attribs, ATTRIBFILE));
			}
			if (file != null) {
				mpe.addPart("upload", new ByteArrayBody(file, filename));
			}
			htp.setEntity(mpe);
		}
		ShockNode sn = (ShockNode)processRequest(htp, ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	public void deleteNode(ShockNodeId id) throws IOException, 
			ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockNodeResponse.class); //triggers throwing errors
	}
	
	public void setNodeReadable(ShockNodeId id, AuthUser user) throws 
			IOException, ShockHttpException,ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl + 
				"?users=" + user.getEmail()); //TODO use userid when shock allows
		final HttpPut htp = new HttpPut(targeturl);
		processRequest(htp, ShockACLResponse.class); //triggers throwing errors
	}
	
	public ShockACL getACLs(ShockNodeId id) throws IOException,
			ShockHttpException, ExpiredTokenException {
		return getACLs(id, new ShockACLType("all"));
	}
	
	public ShockACL getACLs(ShockNodeId id, ShockACLType acl) 
			throws IOException, ShockHttpException, ExpiredTokenException {
		final URI targeturl = nodeurl.resolve(id.getId() + acl.acl);
		final HttpGet htg = new HttpGet(targeturl);
		return (ShockACL)processRequest(htg, ShockACLResponse.class);
	}
	
	public void setNodeWorldReadable(ShockNodeId id) throws IOException,
			ShockHttpException, ExpiredTokenException {
		final List<ShockUserId> acls = getACLs(id, ACL_READ).getRead();
		final List<String> userlist = new ArrayList<String>();
		for (ShockUserId uid: acls) {
			userlist.add(uid.getId());
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl +
				"?users=" + StringUtils.join(userlist, ","));
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockACLResponse.class);
		
	}
	
	//for known good uris ONLY
	private URL uriToUrl(URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException mue) {
			throw new Error(mue); //something is seriously fuxxored
		}
	}
}
