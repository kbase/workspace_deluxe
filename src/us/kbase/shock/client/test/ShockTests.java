package us.kbase.shock.client.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoFileException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;
import us.kbase.shock.client.exceptions.UnvalidatedEmailException;

public class ShockTests {
	
	//TODO token expiry tests - set expired, expire after test - need globus support here
	
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
		noverifiedemail = AuthService.login(uno, pno);
		otherguy = AuthService.login(u2, p2);
		AuthToken t1 = AuthService.login(u1, p1).getToken();
		AuthToken t2 = otherguy.getToken();
		bsc1 = new BasicShockClient(url, t1);
		bsc2 = new BasicShockClient(url, t2);
		bscNoAuth = new BasicShockClient(url);
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

	@Test
	public void addGetDeleteNodeBasic() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc1.deleteNode(sn.getId());
		getDeletedNode(sn.getId());
	}
	
	@Test
	public void getNodeBadId() throws Exception {
		try {
			bsc1.getNode(new ShockNodeId("00000000-0000-0000-0000-000000000000"));
			fail("got node with bad id");
		} catch (ShockHttpException she) {
			assertThat("Bad exception message",
					"us.kbase.shock.client.exceptions.ShockHttpException: 500 Internal Server Error",
					is(she.toString()));
		}
	}
	
	private void getDeletedNode(ShockNodeId id) throws Exception {
		try {
			bsc1.getNode(id);
			fail("Able to retrieve deleted node");
		} catch (ShockHttpException she) {
			assertThat("Bad exception message",
					"us.kbase.shock.client.exceptions.ShockHttpException: 500 Internal Server Error",
					is(she.toString()));
		}
	}
	
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
			sn.getFile();
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
		ShockNode sn = bsc1.addNode(content.getBytes(), name);
		testFile(content, name, sn);
		bsc1.deleteNode(sn.getId());
	}
	
	private void testFile(String content, String name, ShockNode sn) throws Exception {
		ShockNode snget = bsc1.getNode(sn.getId());
		String filecon = new String(bsc1.getFile(sn.getId()));
		String filefromnode = new String(snget.getFile());
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
		assertThat("file size wrong", snget.getFileInformation().getSize(), is(content.length()));
	}
	
	@Test
	public void getNodeWithFileAndAttribs() throws Exception {
		String content = "Like the downy growth on the upper lip of a mediterranean girl";
		String name = "bydemagogueryImeandemagoguery";
		Map<String, Object> attribs = makeSomeAttribs("castellaandlillete");
		ShockNode sn = bsc1.addNode(attribs, content.getBytes(), name);
		testFile(content, name, sn);
		testAttribs(attribs, sn);
		sn.delete();
	}
	
	@Test
	public void invalidFileRequest() throws Exception {
		ShockNode sn = bsc1.addNode();
		try {
			sn.getFile();
			fail("Got file from node w/o file");
		} catch (ShockNoFileException snfe) {
			assertThat("no file exc string incorrect", snfe.toString(), 
					is("us.kbase.shock.client.exceptions.ShockNoFileException: 400 Node has no file"));
		}
	}
	
	@Test
	public void getNodeNulls() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs("wuggawugga");
		try {
			bsc1.addNode(null);
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("attributes"));
		}
		try {
			bsc1.addNode(null, "foo");
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("file"));
		}
		try {
			bsc1.addNode("foo".getBytes(), null);
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("filename"));
		}
		try {
			bsc1.addNode(null, "foo".getBytes(), "foo");
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("attributes"));
		}
		try {
			bsc1.addNode(attribs, null, "foo");
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("file"));
		}
		try {
			bsc1.addNode(attribs, "foo".getBytes(), null);
			fail("called addNode with null value");
		} catch (NullPointerException npe) {
			assertThat("npe message incorrect", npe.getMessage(), is("filename"));
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
				assertThat("Bad exception message", iae.toString(),
						is("java.lang.IllegalArgumentException: id must be a UUID hex string"));
			}
		}
		for (String uuid: badUUIDs) {
			try {
				new ShockUserId(uuid);
				fail("User id accepted invalid id string " + uuid);
			} catch (IllegalArgumentException iae) {
				assertThat("Bad exception message", iae.toString(),
						is("java.lang.IllegalArgumentException: id must be a UUID hex string"));
			}
		}
	}
	
	@Test
	public void generalAcls() throws Exception {
		try {
			new ShockACLType("invalid type") ;
			fail("invalid acl type accepted");
		} catch (IllegalArgumentException iae) {
			assertThat("wrong exception string for bad acl type", iae.toString(),
					is("java.lang.IllegalArgumentException: invalid type is not a valid acl type"));
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
	
	public void checkListLengthIfNotNull(@SuppressWarnings("rawtypes") List list,
			int length) {
		if (list != null) {
			assertTrue(String.format("only %d user in new acl", length),
					list.size() == length);
		}
	}
	
	@Test
	public void addAndReadAclViaNode() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bsc2);
		try {
			sn.setReadable(noverifiedemail);
			fail("set a node readable using an unverified email");
		} catch (UnvalidatedEmailException uee) {
			assertThat("wrong exception string for unverified email", uee.toString(),
					is("us.kbase.shock.client.exceptions.UnvalidatedEmailException: User noemail's email address is not validated"));
		}
		sn.setReadable(otherguy);
		checkAuthAndDelete(sn, bsc2, 2);
	}
	
	@Test
	public void addAndReadAclViaClient() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bsc2);
		try {
			bsc1.setNodeReadable(sn.getId(), noverifiedemail);
			fail("set a node readable using an unverified email");
		} catch (UnvalidatedEmailException uee) {
			assertThat("wrong exception string for unverified email", uee.toString(),
					is("us.kbase.shock.client.exceptions.UnvalidatedEmailException: User noemail's email address is not validated"));
		}
		bsc1.setNodeReadable(sn.getId(), otherguy);
		checkAuthAndDelete(sn, bsc2, 2);
	}
	
	@Test
	public void addAndReadAclViaClientNoAuth() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bscNoAuth);
		bsc1.setNodeWorldReadable(sn.getId());
		checkAuthAndDelete(sn, bscNoAuth, 0);
	}
	
	@Test
	public void addAndReadAclViaNodeNoAuth() throws Exception {
		ShockNode sn = setUpNodeAndCheckAuth(bscNoAuth);
		sn.setWorldReadable();
		checkAuthAndDelete(sn, bscNoAuth, 0);
	}
	
	private ShockNode setUpNodeAndCheckAuth(BasicShockClient c) throws Exception{
		ShockNode sn = bsc1.addNode();
		String expected = 
				"us.kbase.shock.client.exceptions.ShockAuthorizationException: 401 Unauthorized";
		try {
			c.getNode(sn.getId());
			fail("Node is readable with no permissions");
		} catch (ShockAuthorizationException aue) {
			assertThat("auth exception string is correct", aue.toString(),
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
				assertThat("Bad exception message", iae.toString(),
						is("java.lang.IllegalArgumentException: version must be an md5 string"));
			}
		}
		bsc1.deleteNode(sn.getId());
		//will throw errors if doesn't accept md5
		new ShockVersionStamp("e90c05e51aa22e53daec604c815962f3");
	}
}
