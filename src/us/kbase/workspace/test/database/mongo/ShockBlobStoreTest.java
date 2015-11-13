package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthService;
import us.kbase.common.test.TestException;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class ShockBlobStoreTest {
	
	private static ShockBlobStore sb;
	private static DB mongo;
	private static BasicShockClient client;
	private static ShockController shock;
	private static MongoController mongoCon;
	private static TempFilesManager tfm;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");
	private static final String A32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final String COLLECTION = "shock_nodeMap";

	@BeforeClass
	public static void setUpClass() throws Exception {
		String u1 = System.getProperty("test.user1");
		String p1 = System.getProperty("test.pwd1");
		
		tfm = new TempFilesManager(new File(WorkspaceTestCommon.getTempDir()));
		WorkspaceTestCommon.stfuLoggers();
		mongoCon = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				WorkspaceTestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + mongoCon.getTempDir());
		
		String mongohost = "localhost:" + mongoCon.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		mongo = mongoClient.getDB("ShockBackendTest");
		
		shock = new ShockController(
				WorkspaceTestCommon.getShockExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()),
				"***---fakeuser---***",
				mongohost,
				"ShockBackendTest_ShockDB",
				"foo",
				"foo");
		System.out.println("Using Shock temp dir " + shock.getTempDir());
		URL url = new URL("http://localhost:" + shock.getServerPort());
		System.out.println("Testing workspace shock backend pointed at: " + url);
		try {
			sb = new ShockBlobStore(mongo.getCollection(COLLECTION), url, u1, p1);
		} catch (BlobStoreAuthorizationException bsae) {
			throw new TestException("Unable to login with test.user1: " + u1 +
					"\nPlease check the credentials in the test configuration.", bsae);
		}
		client = new BasicShockClient(url, AuthService.login(u1, p1).getToken());
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (shock != null) {
			shock.destroy(WorkspaceTestCommon.deleteTempFiles());
		}
		if (mongoCon != null) {
			mongoCon.destroy(WorkspaceTestCommon.deleteTempFiles());
		}
	}
	
	@Test
	public void storetype() throws Exception {
		assertThat("correct store type", sb.getStoreType(), is("Shock"));
	}
	
	@Test
	public void badInput() throws Exception {
		try {
			sb.saveBlob(new MD5(A32), null, true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
		
		try {
			sb.saveBlob(null, stringToWriteable("foo"), true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}
	
	@Test
	public void badInit() throws Exception {
		DBCollection col = mongo.getCollection(COLLECTION);
		failInit(null, new URL("http://foo.com"), "u", "p");
		failInit(col, null, "u", "p");
		failInit(col, new URL("http://foo.com"), null, "p");
		failInit(col, new URL("http://foo.com"), "u", null);
	}
	
	private void failInit(DBCollection collection, URL url, String user,
			String pwd)
			throws Exception {
		try {
			new ShockBlobStore(collection, url, user, pwd);
		} catch (NullPointerException npe) {
			assertThat("correct exception message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}
	
	@Test
	public void dataWithoutSortMarker() throws Exception {
		String s = "pootypoot";
		ShockNode sn = client.addNode(new ByteArrayInputStream(s.getBytes("UTF-8")), A32, "JSON");
		DBObject rec = new BasicDBObject(Fields.SHOCK_CHKSUM, A32);
		rec.put(Fields.SHOCK_NODE, sn.getId().getId());
		rec.put(Fields.SHOCK_VER, sn.getVersion().getVersion());
		mongo.getCollection(COLLECTION).save(rec);
		MD5 md5 = new MD5(A32);
		ByteArrayFileCache d = sb.getBlob(md5, 
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(s));
		sb.removeBlob(md5);
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		sb.saveBlob(md1, stringToWriteable(data), true);
		ShockNodeId id = new ShockNodeId(sb.getExternalIdentifier(md1));
		assertTrue("Got a valid shock id",
				UUID.matcher(id.getId()).matches());
		assertThat("Ext id is the shock node", id.getId(),
				is(sb.getExternalIdentifier(md1)));
		MD5 md1copy = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		ByteArrayFileCache d = sb.getBlob(md1copy, 
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as sorted", d.isSorted(), is(true));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(data));
		sb.saveBlob(md1, stringToWriteable(data), true); //should be able to save the same thing twice with no error
		
		sb.saveBlob(md1, stringToWriteable(data), false); //this should do nothing
		assertThat("sorted still true", sb.getBlob(md1copy,
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm))
				.isSorted(), is(true));
		
		MD5 md2 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2");
		String data2 = "this is also a blob yo";
		sb.saveBlob(md2, stringToWriteable(data2), false);
		d = sb.getBlob(md2,
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));
		
		sb.removeBlob(md1);
		sb.removeBlob(md2);
		failGetBlob(md1);
	}
	
	@Test
	public void getNonExistantBlob() throws Exception {
		failGetBlob(new MD5(A32));
	}

	private void failGetBlob(MD5 md5) throws Exception {
		try {
			sb.getBlob(md5,
					new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
			fail("getblob should throw exception");
		} catch (NoSuchBlobException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("No blob saved with chksum "
					+ md5.getMD5()));
		}
	}
	
	@Test
	public void removeNonExistantBlob() throws Exception {
		sb.removeBlob(new MD5(A32)); //should silently not remove anything
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		sb.saveBlob(md1, stringToWriteable(data), true);
		sb.removeAllBlobs();
		
	}
	
	@Test
	public void removeAllBlobs() throws Exception {
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		sb.saveBlob(md1, stringToWriteable(data), true);
		sb.removeAllBlobs();
		failGetBlob(md1);
	}
	
	private static Writable stringToWriteable(final String s) {
		return new Writable() {
			@Override
			public void write(OutputStream w) throws IOException {
				w.write(s.getBytes("UTF-8"));
			}
			@Override
			public void releaseResources() throws IOException {
			}
		};
	}
}
