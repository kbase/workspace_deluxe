package us.kbase.shock.client.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
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
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockVersionStamp;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNodeDeletedException;

public class ShockTests {
	
	private static BasicShockClient bsc1;
	private static BasicShockClient bsc2;
	private static BasicShockClient bscNoAuth;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		URL url = new URL(System.getProperty("test.shock.url"));
		System.out.println("Testing shock clients pointed at: " + url);
		String u1 = System.getProperty("test.user1");
		String u2 = System.getProperty("test.user2");
		String p1 = System.getProperty("test.pwd1");
		String p2 = System.getProperty("test.pwd2");
		AuthToken t1 = AuthService.login(u1, p1).getToken();
		AuthToken t2 = AuthService.login(u2, p2).getToken();
		bsc1 = new BasicShockClient(url, t1);
		bsc2 = new BasicShockClient(url, t2);
		bscNoAuth = new BasicShockClient(url);
	}
	
	@Test
	public void testShockUrl() throws Exception {
		URL url = bsc1.getShockUrl();
		BasicShockClient b = new BasicShockClient(url); //will choke if bad url
		assertThat("url is preserved", b.getShockUrl().toString(), is(url.toString()));
		List<String> badURLs = Arrays.asList("ftp://thing.us/",
			"http://google.com/", "http://kbase.us/services/idserver/");
		for (String burl: badURLs) {
			try {
				new BasicShockClient(new URL(burl));
				fail("init'd client with bad url");
			} catch (InvalidShockUrlException isu) {}
		}
		String newurl = "https://kbase.us/services/shock-api/";
		BasicShockClient b2 = new BasicShockClient(new URL(newurl + "foo/"));
		assertThat("https url not preserved", b2.getShockUrl().toString(), is(newurl));
	}

	@Test
	public void addGetDeleteNodeBasic() throws Exception {
		ShockNode sn = bsc1.addNode();
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		bsc1.deleteNode(sn.getId());
		getDeletedNode(sn.getId());
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
	
	private Map<String,Object> makeSomeAttribs() {
		Map<String, Object> attribs = new HashMap<String, Object>();
		List<Object> l = new ArrayList<Object>();
		l.add("alist");
		Map<String, Object> inner = new HashMap<String, Object>();
		inner.put("entity", "enigma");
		l.add(inner);
		attribs.put("foo", l);
		return attribs;
	}
	
	@Test
	public void getNodeWithAttribs() throws Exception {
		Map<String, Object> attribs = makeSomeAttribs();
		ShockNode sn = bsc1.addNode(attribs);
		ShockNode snget = bsc1.getNode(sn.getId());
		assertThat("get node != add Node output", snget.toString(), is(sn.toString()));
		assertThat("attribs altered", snget.getAttributes(), is(attribs));
		bsc1.deleteNode(sn.getId());
	}
	
	@Test
	public void getNodeWithFile() throws Exception {
		String content = "Been shopping? No, I've been shopping";
		String name = "apistonengine.recipe";
		ShockNode sn = bsc1.addNode(content.getBytes(), name);
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
		bsc1.deleteNode(sn.getId());
	}
	
	@Test
	public void testVersion() throws Exception {
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
	
	public static void main(String[] args) throws Exception {
		AuthUser au = AuthService.login("x", "x");
//		System.out.println(au);
		BasicShockClient bsc = new BasicShockClient(new URL("http://localhost:7044"), au.getToken());
		System.out.println("***Add node");
		Map<String, Object> attribs = new HashMap<String, Object>();
		attribs.put("foo", "newbar");
		ShockNode node = bsc.addNode(attribs, "some serious crap right here".getBytes(), "seriouscrapfile");
		System.out.println(node);
		System.out.println("***Get node");
		System.out.println(bsc.getNode(node.getId()));
		System.out.println("***Get file");
		System.out.println(new String(bsc.getFile(node.getId())));
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
			bsc.getFile(node2.getId());
		} catch (ShockHttpException she) {
			System.out.println(she);
		}
		ShockNode node2get = bsc.getNode(node2.getId());
		System.out.println(bsc.getNode(node2get.getId()));
		
		System.out.println("***set node readable***");
		AuthUser au2 = AuthService.login("x", "x");
		bsc.setNodeReadable(node2get.getId(), au2);
		System.out.println("***get all ACLs***");
		System.out.println(bsc.getACLs(node2get.getId()));
		System.out.println(bsc.getACLs(node2get.getId(), new ShockACLType("all")));
		System.out.println("***get read ACLs***");
		System.out.println(bsc.getACLs(node2get.getId(), new ShockACLType("read")));
		System.out.println("***get write ACLs***");
		System.out.println(bsc.getACLs(node2get.getId(), new ShockACLType("write")));
		System.out.println("***get delete ACLs***");
		System.out.println(bsc.getACLs(node2get.getId(), new ShockACLType("delete")));
		System.out.println("***get owner ACLs***");
		System.out.println(bsc.getACLs(node2get.getId(), new ShockACLType("owner")));
		System.out.println("***set world readable***");
		bsc.setNodeWorldReadable(node2get.getId());
		System.out.println("***get all ACLs***");
		System.out.println(bsc.getACLs(node2get.getId()));
		System.out.println("***read with no creds***");
		System.out.println(bscNoAuth.getNode(node2get.getId()));
		
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
		
		//TODO test errors
		
		
	}
}
