package us.kbase.shock.client.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.gc.iotools.stream.is.InputStreamFromOutputStream;
import com.gc.iotools.stream.os.OutputStreamToInputStream;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.TokenExpiredException;
import us.kbase.common.test.TestException;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockNoFileException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.shock.client.exceptions.UnvalidatedEmailException;

public class ShockTests {
	
	private static BasicShockClient bsc1;
	private static BasicShockClient bsc2;
	private static BasicShockClient bscNoAuth;
	private static AuthUser otherguy;
	private static AuthUser noverifiedemail;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.out.println("Java: " + System.getProperty("java.runtime.version"));
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String u2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		String uno = System.getProperty("test.user.noemail");
		String pno = System.getProperty("test.pwd.noemail");
		
		System.out.println("Logging in users");
		try {
			noverifiedemail = AuthService.login(uno, pno);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user.noemail: " + uno +
					"\nPlease check the credentials in the test configuration.", ae);
		}
		System.out.println("Logged in user w/o verified email");
		try {
			otherguy = AuthService.login(u2, p2);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user2: " + u2 +
					"\nPlease check the credentials in the test configuration.", ae);
		}
		System.out.println("Logged in user2");
		AuthUser user1;
		try {
			user1 = AuthService.login(u1, p1);
		} catch (AuthException ae) {
			throw new TestException("Unable to login with test.user1: " + u1 +
					"\nPlease check the credentials in the test configuration.", ae);
		}
		System.out.println("Logged in user1");
		if (user1.getEmail().equals(otherguy.getEmail())) {
			throw new TestException("The email addresses of test.user1 and " + 
					"test.user2 are the same. Please provide test users with different email addresses.");
		}
		try {
			bsc1 = new BasicShockClient(url, user1.getToken());
			bsc2 = new BasicShockClient(url, otherguy.getToken());
		} catch (IOException ioe) {
			throw new TestException("Couldn't set up shock client: " +
					ioe.getLocalizedMessage());
		}
		bscNoAuth = new BasicShockClient(url);
		System.out.println("Set up shock clients");
	}
	
	@Test
	public void setExpiredToken() throws Exception {
		AuthToken exptok = new AuthToken(otherguy.getTokenString(), 0);
		Thread.sleep(5000); //account for a little clockskew
		try {
			bsc2.updateToken(exptok);
			fail("accepted expired token on update");
		} catch (TokenExpiredException tee) {}
	}
	
	@Test
	public void setOldToken() throws Exception {
		AuthToken orig = otherguy.getToken();
		AuthToken exptok = new AuthToken(orig.toString(),
				(new Date().getTime() - orig.getIssueDate().getTime())/1000 + 1);
		bsc2.updateToken(exptok);
		Thread.sleep(2000);
		assertTrue("token is expired", bsc2.isTokenExpired());
		try {
			bsc2.addNode();
			fail("Added node with expired token");
		} catch (TokenExpiredException tee) {}
		
		bsc2.updateToken(orig); //restore to std state
		assertFalse("token is now valid", bsc2.isTokenExpired());
	}
	

	@Test
	public void shockUrl() throws Exception {
		URL url = bsc1.getShockUrl();
		BasicShockClient b = new BasicShockClient(url); //will choke if bad url
		assertThat("url is preserved", b.getShockUrl().toString(), is(url.toString()));
		//Note using cdmi to test for cases where valid json is returned but
		//the id field != Shock. However, sometimes the cdmi server doesn't 
		//return an id, which I assume is a bug (see https://atlassian.kbase.us/browse/KBASE-200)
		List<String> badURLs = Arrays.asList("ftp://thing.us/",
			"http://google.com/", "http://kbase.us/services/cdmi_api/",
			"http://kbase.us/services/shock-api/node/9u8093481-1758175-157-15/");
		for (String burl: badURLs) {
			try {
				new BasicShockClient(new URL(burl));
				fail("init'd client with bad url");
			} catch (InvalidShockUrlException isu) {}
		}
		String newurl = "https://kbase.us/services/shock-api/";
		BasicShockClient b2 = new BasicShockClient(new URL(newurl + "foo/"));
		assertThat("https url not preserved", b2.getShockUrl().toString(), is(newurl));
		String newurl2 = "https://kbase.us/services/shock-api";
		BasicShockClient b3 = new BasicShockClient(new URL(newurl2));
		assertThat("url w/o trailing slash fails", b3.getShockUrl().toString(),
				is(newurl2 + "/"));
		
	}

	//@Ignore //TODO unignore when shock is updated
	@Test
	public void addGetDeleteNodeBasic() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc1.deleteNode(sn.getId());
		getDeletedNode(sn.getId());
	}
	
	//@Ignore //TODO unignore when shock is updated
	@Test
	public void getNodeBadId() throws Exception {
		try {
			bsc1.getNode(new ShockNodeId("00000000-0000-0000-0000-000000000000"));
			fail("got node with bad id");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node does not exist"));
		}
	}
	
	private void getDeletedNode(ShockNodeId id) throws Exception {
		try {
			bsc1.getNode(id);
			fail("Able to retrieve deleted node");
		} catch (ShockNoNodeException snne) {
			assertThat("Bad exception message", snne.getLocalizedMessage(),
					is("Node does not exist"));
		}
	}
	
	//@Ignore //TODO unignore when shock is updated
	@Test
	public void deleteByNode() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNodeId id = sn.getId();
		sn.delete();
		getDeletedNode(id);
		try {
			sn.delete();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getAttributes();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFile(new ByteArrayOutputStream());
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getFileInformation();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getId();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
		try {
			sn.getVersion();
			fail("Method ran on deleted node");
		} catch (ShockNodeDeletedException snde) {}
	}
	
	private Map<String,Object> makeSomeAttribs(String astring) {
		Map<String, Object> attribs = new HashMap<String, Object>();
		List<Object> l = new ArrayList<Object>();
		l.add("alist");
		l.add(astring);
		Map<String, Object> inner = new HashMap<String, Object>();
		inner.put("entity", "enigma");
		l.add(inner);
		attribs.put("foo", l);
		return attribs;
	}
	
	@Test
	public void getNodeWithAttribs() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs("funkycoldmedina");
		ShockNode sn = bsc1.addNode(attribs);
		testAttribs(attribs, sn);
		bsc1.deleteNode(sn.getId());
	}
	
	private void testAttribs(Map<String, Object> attribs, ShockNode sn) throws Exception {
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		assertThat("attribs altered", snget.getAttributes(), is(attribs));
	}
	
	@Test
	public void getNodeWithFile() throws Exception {
		String content = "Been shopping? No, I've been shopping";
		String name = "apistonengine.recipe";
		ShockNode sn = addNode(bsc1, content, name);
		testFile(content, name, sn);
		bsc1.deleteNode(sn.getId());
	}
	
	private ShockNode addNode(BasicShockClient bsc, String content, String name)
			throws Exception {
		return bsc.addNode(new ReaderInputStream(new StringReader(content)),
				content.getBytes(StandardCharsets.UTF_8).length, name);
	}
	
	private ShockNode addNode(BasicShockClient bsc, Map<String, Object> attribs,
			String content, String name)
			throws Exception {
		return bsc.addNode(attribs, new ReaderInputStream(new StringReader(content)),
				content.getBytes(StandardCharsets.UTF_8).length, name);
	}
	
	@Ignore //TODO uncomment when in separate repo
	@Test
	public void saveAndGetNodeWith4GBFile() throws Exception {
		long smallfilesize = 1001000000;
		final long filesize = smallfilesize * 4;
		StringBuilder sb = new StringBuilder();
		sb.append("abcd");
		sb.appendCodePoint(8364);
		final int teststrlenUTF8 = 7; //filesize mod this must = 0
		final long writes = filesize / teststrlenUTF8;
		final String testString = sb.toString();
		
		InputStreamFromOutputStream<String> isos =
				new InputStreamFromOutputStream<String>() {
			
			@Override
			public String produce(final OutputStream dataSink)
					throws Exception {
				Writer writer = new OutputStreamWriter(dataSink,
						StandardCharsets.UTF_8);
				for (int i = 0; i < writes; i++) {
					writer.write(testString);
				}
				writer.flush();
				writer.close();
				return null;
			}
		};
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foo", "bar");

		ShockNode sn = bsc1.addNode(attribs, isos, filesize, "somefile");
		isos.close();
		
		OutputStreamToInputStream<String> osis =
				new OutputStreamToInputStream<String>() {
					
			@Override
			protected String doRead(InputStream is) throws Exception {
				int read = 1;
				byte[] data = new byte[teststrlenUTF8];
				long size = 0;
				byte[] shrt = null;
				while (read > 0) {
					if (shrt != null) {
						is.read(data, shrt.length, teststrlenUTF8 - shrt.length);
						for (int i = 0; i < shrt.length; i++) {
							data[i] = shrt[i];
						}
						read = teststrlenUTF8;
						shrt = null;
					} else {
						read = is.read(data);
					}
					if (read > 0) {
						if (read < teststrlenUTF8) {
							shrt = Arrays.copyOf(data, read);
						} else {
							assertThat("file incorrect at pos " + size, 
									new String(data, StandardCharsets.UTF_8),
									is(testString));
							size += teststrlenUTF8;
						}
					}
				}
				assertThat("correct file size", size, is(filesize));
				return null;
			}
		};
		bsc1.getFile(sn, osis);
		osis.close();
		bsc1.deleteNode(sn.getId());
	}
	
	private void testFile(String content, String name, ShockNode sn) throws Exception {
		ShockNode snget = bsc1.getNode(sn.getId());
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bsc1.getFile(sn, bos);
		String filecon = bos.toString(StandardCharsets.UTF_8.name());
		ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		snget.getFile(bos2);
		String filefromnode = bos2.toString(StandardCharsets.UTF_8.name());
		Set<String> digestTypes = snget.getFileInformation().getChecksumTypes();
		assertTrue(digestTypes.contains("md5"));
		assertTrue(digestTypes.contains("sha1"));
		assertThat("unequal md5", snget.getFileInformation().getChecksum("md5"),
				is(DigestUtils.md5Hex(content)));
		assertThat("unequal sha1", snget.getFileInformation().getChecksum("sha1"),
				is(DigestUtils.sha1Hex(content)));
		try {
			snget.getFileInformation().getChecksum("this is not a checksum type");
			fail("got checksum type that doesn't exist");
		} catch (IllegalArgumentException iae) {
			assertThat("exception string incorrect", 
					"java.lang.IllegalArgumentException: No such checksum type: this is not a checksum type",
					is(iae.toString()));
		}
		assertThat("file from node != file from client", filefromnode, is(filecon));
		assertThat("file content unequal", filecon, is(content));
		assertThat("file name unequal", snget.getFileInformation().getName(), is(name));
		assertThat("file size wrong", snget.getFileInformation().getSize(), is((long) content.length()));
	}
	
	@Test
	public void getNodeWithFileAndAttribs() throws Exception {
		String content = "Like the downy growth on the upper lip of a mediterranean girl";
		String name = "bydemagogueryImeandemagoguery";
		Map<String, Object> attribs = makeSomeAttribs("castellaandlillete");
		ShockNode sn = addNode(bsc1, attribs, content, name);
		testFile(content, name, sn);
		testAttribs(attribs, sn);
		sn.delete();
	}
	
	@Test
	public void invalidFileRequest() throws Exception {
		ShockNode sn = bsc1.addNode();
		try {
			sn.getFile(new ByteArrayOutputStream());
			fail("Got file from node w/o file");
		} catch (ShockNoFileException snfe) {
			assertThat("no file exc string incorrect", snfe.getLocalizedMessage(), 
					is("Node has no file"));
		}
		sn.delete();
	}
	
	@Test
	public void getNodeNulls() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs("wuggawugga");
		try {
			bsc1.addNode(null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("attributes may not be null"));
		}
		try {
			bsc1.addNode(null, 3, "foo");  //TODO test filesize < 1
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("file may not be null"));
		}
		try {
			addNode(bsc1, "foo", null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null"));
		}
		try {
			addNode(bsc1, null, "foo", "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("attributes may not be null"));
		}
		try {
			bsc1.addNode(attribs, null, 6, "foo");
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("file may not be null"));
		}
		try {
			addNode(bsc1, attribs, "foo", null);
			fail("called addNode with null value");
		} catch (IllegalArgumentException npe) {
			assertThat("npe message incorrect", npe.getMessage(),
					is("filename may not be null"));
		}
	}
	
	@Test
	public void ids() throws Exception {
		//will throw exception if doesn't process good uuid
		new ShockUserId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		ShockNodeId id1 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		ShockNodeId id2 = new ShockNodeId("cbf19927-1e04-456c-b2c3-812edd90fa68");
		assertTrue("id equality failed", id1.equals(id1));
		assertTrue("id state failed", id1.equals(id2));
		assertFalse("non id equal to id", id1.equals(new ArrayList<Object>()));
		
		
		List<String> badUUIDs = Arrays.asList("cbf19927a1e04-456c-b2c3-812edd90fa68",
				"cbf19927-1e04-456c1-b2c3-812edd90fa68", "acbf19927-1e04-456c-b2c3-812edd90fa68",
				"cbf19927-1e04-456c-b2c3-812gdd90fa68");
		for (String uuid: badUUIDs) {
			try {
				new ShockNodeId(uuid);
				fail("Node id accepted invalid id string " + uuid);
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("id must be a UUID hex string"));
			}
		}
		for (String uuid: badUUIDs) {
			try {
				new ShockUserId(uuid);
				fail("User id accepted invalid id string " + uuid);
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("id must be a UUID hex string"));
			}
		}
	}
	
	@Test
	public void generalAcls() throws Exception {
		try {
			new ShockACLType("invalid type") ;
			fail("invalid acl type accepted");
		} catch (IllegalArgumentException iae) {
			assertThat("wrong exception string for bad acl type", iae.getLocalizedMessage(),
					is("invalid type is not a valid acl type"));
		}
		ShockACLType owner = new ShockACLType("owner");
		ShockNode sn = bsc1.addNode();
		assertTrue("acl access methods produce different acls",
				sn.getACLs().equals(bsc1.getACLs(sn.getId())));
		ShockACL acl1 = sn.getACLs(owner);
		ShockACL acl2 = bsc1.getACLs(sn.getId(), owner);
		assertTrue("acl owner access methods produce different acls",
				acl1.equals(acl2));
		assertTrue("owners for same node are different",
				acl1.getOwner().equals(acl1.getOwner()));
		assertTrue("same acls aren't equal", acl1.equals(acl1));
		assertFalse("acl equal to different type", acl1.equals(owner));
		
		List<ShockACLType> acls = Arrays.asList(new ShockACLType("all"),
				new ShockACLType("read"), new ShockACLType("write"),
				new ShockACLType("delete"), new ShockACLType("all"),
				new ShockACLType());
		for (ShockACLType acl: acls) {
			ShockACL sacl = sn.getACLs(acl);
			assertTrue(String.format("%s subset of acls are different", acl.getType()),
					sacl.equals(bsc1.getACLs(sn.getId(), acl)));
			checkListLengthIfNotNull(sacl.getRead(), 1);
			checkListLengthIfNotNull(sacl.getWrite(), 1);
			checkListLengthIfNotNull(sacl.getDelete(), 1);
		}
		sn.delete();
	}
	
	private void checkListLengthIfNotNull(@SuppressWarnings("rawtypes") List list,
			int length) {
		if (list != null) {
			assertTrue(String.format("only %d user in new acl", length),
					list.size() == length);
		}
	}
	
	@Test
	public void addAndReadAclViaNode() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bsc2, true);
		try {
			sn.setReadable(noverifiedemail);
			fail("set a node readable using an unverified email");
		} catch (UnvalidatedEmailException uee) {
			assertThat("wrong exception string for unverified email", uee.getLocalizedMessage(),
					is(String.format("User %s's email address is not validated",
					noverifiedemail.getUserId())));
		}
		sn.setReadable(otherguy);
		checkAuthAndDelete(sn, bsc2, 2);
	}
	
	@Test
	public void addAndReadAclViaClient() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bsc2, true);
		try {
			bsc1.setNodeReadable(sn.getId(), noverifiedemail);
			fail("set a node readable using an unverified email");
		} catch (UnvalidatedEmailException uee) {
			assertThat("wrong exception string for unverified email", uee.getLocalizedMessage(),
					is(String.format("User %s's email address is not validated",
					noverifiedemail.getUserId())));
		}
		bsc1.setNodeReadable(sn.getId(), otherguy);
		checkAuthAndDelete(sn, bsc2, 2);
	}
	
	@Test
	public void addAndReadAclViaClientNoAuth() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bscNoAuth, false);
		bsc1.setNodeWorldReadable(sn.getId());
		checkAuthAndDelete(sn, bscNoAuth, 0);
	}
	
	@Test
	public void addAndReadAclViaNodeNoAuth() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bscNoAuth, false);
		sn.setWorldReadable();
		checkAuthAndDelete(sn, bscNoAuth, 0);
	}
	
	private ShockNode setUpNodeAndCheckAuth(BasicShockClient c, boolean auth)
			throws Exception{
		ShockNode sn = bsc1.addNode();
		String expected;
		if (auth) {
			expected = "Unauthorized";
		} else {
			//if Authorization.read = false, then you get a No Auth error
			expected = "Unauthorized"; //"No Authorization";
		}
		try {
			c.getNode(sn.getId());
			fail("Node is readable with no permissions");
		} catch (ShockAuthorizationException aue) {
			assertThat("auth exception string is correct", aue.getLocalizedMessage(),
					is(expected));
		}
		return sn;
	}
	
	private void checkAuthAndDelete(ShockNode sn, BasicShockClient c, int size)
			throws Exception {
		assertThat("Setting read perms failed", size, 
				is(sn.getACLs(new ShockACLType("read")).getRead().size()));
		sn = bsc1.getNode(sn.getId()); //version stamp changed
		ShockNode sn2 = c.getNode(sn.getId());
		assertThat("different users see different nodes", sn.toString(),
				is(sn2.toString()));
		sn.delete();
	}
	
	@Test
	public void version() throws Exception {
		ShockNode sn = bsc1.addNode();
		sn.getVersion().getVersion(); //not much else to do here
		List<String> badMD5s = Arrays.asList("fe90c05e51aa22e53daec604c815962g3",
				"e90c05e51aa22e53daec604c815962f", "e90c05e51aa-2e53daec604c815962f3",
				"e90c05e51aa22e53daec604c815962f31");
		for (String md5: badMD5s) {
			try {
				new ShockVersionStamp(md5);
				fail("Version stamp accepted invalid version string");
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.getLocalizedMessage(),
						is("version must be an md5 string"));
			}
		}
		bsc1.deleteNode(sn.getId());
		//will throw errors if doesn't accept md5
		new ShockVersionStamp("e90c05e51aa22e53daec604c815962f3");
	}
}
