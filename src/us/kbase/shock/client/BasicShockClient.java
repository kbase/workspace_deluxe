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
import us.kbase.auth.TokenExpiredException;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.UnvalidatedEmailException;

/**
 * A basic client for shock. Creating nodes, deleting nodes, getting a subset of node data,
 * and altering read acls is currently supported.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class BasicShockClient {
	
	private final URI baseurl;
	private final URI nodeurl;
	private final HttpClient client = new DefaultHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private AuthToken token = null;
	
	private static final String AUTH = "Authorization";
	private static final String OAUTH = "OAuth ";
	private static final String DOWNLOAD = "/?download";
	private static final String ATTRIBFILE = "attribs";
	private static final ShockACLType ACL_READ = new ShockACLType("read");
	
	/**
	 * Create a new shock client authorized to act as a shock user.
	 * @param url the location of the shock server.
	 * @param token the authorization token to present to shock.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public BasicShockClient(URL url, AuthToken token) throws IOException, 
			InvalidShockUrlException, TokenExpiredException {
		this(url);
		updateToken(token);
	}
	
	/**
	 * Create a new shock client.
	 * @param url the location of the shock server.
	 * @throws IOException if an IO problem occurs.
	 * @throws InvalidShockUrlException if the <code>url</code> does not reference
	 * a shock server.
	 */
	@SuppressWarnings("unchecked")
	public BasicShockClient(URL url) throws IOException, 
			InvalidShockUrlException {

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
	
	/**
	 * Replace the token this client presents to the shock server.
	 * @param token the new token
	 * @throws TokenExpiredException if the <code>token</code> is expired.
	 */
	public void updateToken(AuthToken token) throws TokenExpiredException {
		if (token == null) {
			this.token = null;
			return;
		}
		if (token.isExpired()) {
			throw new TokenExpiredException(token.getTokenId());
		}
		this.token = token;
	}
	
	/**
	 * Check the token's validity.
	 * @return <code>true</code> if the client has no auth token or the token
	 * is expired, <code>false</code> otherwise.
	 */
	public boolean isTokenExpired() {
		if (token == null || token.isExpired()) {
			return true;
		}
		return false;
	}
	
	/** 
	 * Get the url of the shock server this client communicates with.
	 * @return the shock url.
	 */
	public URL getShockUrl() {
		return uriToUrl(baseurl);
	}
	
	private <T extends ShockResponse> ShockData
			processRequest(HttpRequestBase httpreq, Class<T> clazz) throws
			IOException, ShockHttpException, TokenExpiredException {
		authorize(httpreq);
		final HttpResponse response = client.execute(httpreq);
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
			TokenExpiredException {
		if (token != null) {
			if (token.isExpired()) {
				throw new TokenExpiredException(token.getTokenId());
			}
			httpreq.setHeader(AUTH, OAUTH + token);
		}
	}

	/** 
	 * Gets a node from the shock server. Note the object returned 
	 * represents the shock node's state at the time getNode() was called
	 * and does not update further.
	 * @param id the ID of the shock node.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode getNode(ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpGet htg = new HttpGet(targeturl);
		final ShockNode sn = (ShockNode)processRequest(htg, ShockNodeResponse.class);
		sn.addClient(this);
		return sn;
	}
	
	/**
	 * Get a file from a shock node.
	 * @param id the ID of the shock node.
	 * @return the contents of the file.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the file could not be fetched from shock.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public byte[] getFile(ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
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
	
	/**
	 * Creates an empty node on the shock server.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode() throws IOException, ShockHttpException,
			TokenExpiredException {
		return _addNode(null, null, null);
	}
	
	/**
	 * Creates an node on the shock server with user-specified attributes.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(Map<String, Object> attributes) throws
			IOException, ShockHttpException, JsonProcessingException,
			TokenExpiredException {
		if (attributes == null) {
			throw new NullPointerException("attributes");
		}
		return _addNode(attributes, null, null);
	}
	
	/**
	 * Creates a node on the shock server containing a file.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(byte[] file, String filename) throws IOException,
			ShockHttpException, TokenExpiredException {
		if (file == null) {
			throw new NullPointerException("file");
		}
		if (filename == null) {
			throw new NullPointerException("filename");
		}
		return _addNode(null, file, filename);
	}
	
	/**
	 * Creates an node on the shock server with user-specified attributes and 
	 * a file.
	 * @return a shock node object.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be created.
	 * @throws JsonProcessingException if the <code>attributes</code> could
	 * not be serialized to JSON.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockNode addNode(Map<String, Object> attributes, byte[] file,
			String filename) throws IOException, ShockHttpException,
			JsonProcessingException, TokenExpiredException {
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
			JsonProcessingException, TokenExpiredException {
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
	
	/**
	 * Deletes a node on the shock server.
	 * @param id the node to delete.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node could not be deleted.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void deleteNode(ShockNodeId id) throws IOException, 
			ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId());
		final HttpDelete htd = new HttpDelete(targeturl);
		processRequest(htd, ShockNodeResponse.class); //triggers throwing errors
	}
	
	/**
	 * Adds a user to a shock node's read access control list (ACL).
	 * @param id the node to modify.
	 * @param user the user to be added to the read ACL.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node ACL could not be altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 * @throws UnvalidatedEmailException if the <code>user</code>'s email
	 * address is unvalidated.
	 */
	public void setNodeReadable(ShockNodeId id, AuthUser user) throws 
			IOException, ShockHttpException,TokenExpiredException,
			UnvalidatedEmailException {
		if (!user.isEmailValidated()) {
			throw new UnvalidatedEmailException(String.format(
					"User %s's email address is not validated",
					user.getUserId()));
		}
		final URI targeturl = nodeurl.resolve(id.getId() + ACL_READ.acl + 
				"?users=" + user.getEmail()); //TODO use userid when shock allows
		final HttpPut htp = new HttpPut(targeturl);
		processRequest(htp, ShockACLResponse.class); //triggers throwing errors
	}
	
	/**
	 * Makes a node world readable.
	 * @param id the node to modify.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node read access control list could not be
	 * altered.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public void setNodeWorldReadable(ShockNodeId id) throws IOException,
	ShockHttpException, TokenExpiredException {
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
	
	/**
	 * Retrieves all the access control lists (ACLs) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @return the ACLs for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control lists could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(ShockNodeId id) throws IOException,
			ShockHttpException, TokenExpiredException {
		return getACLs(id, new ShockACLType());
	}
	
	/**
	 * Retrieves a specific access control list (ACL) from the shock server for
	 * a node. Note the object returned represents the shock node's state at
	 * the time getACLs() was called and does not update further.
	 * @param id the node to query.
	 * @param acl the type of ACL to retrieve.
	 * @return the ACL for the node.
	 * @throws IOException if an IO problem occurs.
	 * @throws ShockHttpException if the node's access control list could not be
	 * retrieved.
	 * @throws TokenExpiredException if the client authorization token has
	 * expired.
	 */
	public ShockACL getACLs(ShockNodeId id, ShockACLType acl) 
			throws IOException, ShockHttpException, TokenExpiredException {
		final URI targeturl = nodeurl.resolve(id.getId() + acl.acl);
		final HttpGet htg = new HttpGet(targeturl);
		return (ShockACL)processRequest(htg, ShockACLResponse.class);
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
