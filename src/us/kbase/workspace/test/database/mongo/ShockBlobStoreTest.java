package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.zafarkhaja.semver.Version;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.common.test.controllers.shock.ShockController;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.Fields;
import us.kbase.workspace.database.mongo.ShockBlobStore;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public class ShockBlobStoreTest {
	
	private static ShockBlobStore sb;
	private static DB mongo;
	private static BasicShockClient client;
	private static ShockController shock;
	private static MongoController mongoCon;
	private static TempFilesManager tfm;
	private static AuthToken token;
	
	private static final Pattern UUID =
			Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");
	private static final String A32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final String COLLECTION = "shock_nodeMap";

	@BeforeClass
	public static void setUpClass() throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl()));
		token = TestCommon.getToken(1, auth);
		
		tfm = new TempFilesManager(new File(TestCommon.getTempDir()));
		TestCommon.stfuLoggers();
		mongoCon = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + mongoCon.getTempDir());
		
		String mongohost = "localhost:" + mongoCon.getServerPort();
		MongoClient mongoClient = new MongoClient(mongohost);
		mongo = mongoClient.getDB("ShockBackendTest");
		
		shock = new ShockController(
				TestCommon.getShockExe(),
				TestCommon.getShockVersion(),
				Paths.get(TestCommon.getTempDir()),
				"***---fakeuser---***",
				mongohost,
				"ShockBackendTest_ShockDB",
				"foo",
				"foo",
				TestCommon.getGlobusUrl());
		System.out.println("Shock controller version: " + shock.getVersion());
		if (shock.getVersion() == null) {
			System.out.println("Unregistered version - Shock may not start correctly");
		}
		System.out.println("Using Shock temp dir " + shock.getTempDir());
		URL url = new URL("http://localhost:" + shock.getServerPort());
		System.out.println("Testing workspace shock backend pointed at: " + url);
		System.out.println("Logging in with auth service " + auth);
		sb = new ShockBlobStore(mongo.getCollection(COLLECTION), url, token);
		client = new BasicShockClient(url, token);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (shock != null) {
			shock.destroy(TestCommon.getDeleteTempFiles());
		}
		if (mongoCon != null) {
			mongoCon.destroy(TestCommon.getDeleteTempFiles());
		}
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
			sb.saveBlob(null, IOUtils.toInputStream("foo"), true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}
	
	@Test
	public void badInit() throws Exception {
		DBCollection col = mongo.getCollection(COLLECTION);
		failInit(null, new URL("http://foo.com"), token);
		failInit(col, null, token);
		failInit(col, new URL("http://foo.com"), null);
	}
	
	private void failInit(
			final DBCollection collection,
			final URL url,
			final AuthToken tp)
			throws Exception {
		try {
			new ShockBlobStore(collection, url, tp);
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
		sb.saveBlob(md1, IOUtils.toInputStream(data), true);
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
		sb.saveBlob(md1, IOUtils.toInputStream(data), true); //should be able to save the same thing twice with no error
		
		sb.saveBlob(md1, IOUtils.toInputStream(data), false); //this should do nothing
		assertThat("sorted still true", sb.getBlob(md1copy,
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm))
				.isSorted(), is(true));
		
		MD5 md2 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2");
		String data2 = "this is also a blob yo";
		sb.saveBlob(md2, IOUtils.toInputStream(data2), false);
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
		sb.saveBlob(md1, IOUtils.toInputStream(data), true);
		sb.removeAllBlobs();
		
	}
	
	@Test
	public void removeAllBlobs() throws Exception {
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		sb.saveBlob(md1, IOUtils.toInputStream(data), true);
		sb.removeAllBlobs();
		failGetBlob(md1);
	}
	
	@Test
	public void status() throws Exception {
		List<DependencyStatus> deps = sb.status();
		assertThat("incorrect number of deps", deps.size(), is(1));
		DependencyStatus dep = deps.get(0);
		assertThat("incorrect fail", dep.isOk(), is(true));
		assertThat("incorrect name", dep.getName(), is("Shock"));
		assertThat("incorrect status", dep.getStatus(), is("OK"));
		//should throw an error if not a semantic version
		Version.valueOf(dep.getVersion());
	}
}
