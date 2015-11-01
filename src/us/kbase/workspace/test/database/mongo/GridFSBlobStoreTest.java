package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;

import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.Writable;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.mongo.GridFSBlobStore;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreException;
import us.kbase.workspace.test.WorkspaceTestCommon;

public class GridFSBlobStoreTest {
	
	
	private static GridFSBlobStore gfsb;
	private static GridFS gfs;
	private static MongoController mongo;
	private static TempFilesManager tfm;
	
	private static final String a32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		tfm = new TempFilesManager(new File(WorkspaceTestCommon.getTempDir()));
		mongo = new MongoController(WorkspaceTestCommon.getMongoExe(),
				Paths.get(WorkspaceTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " +
				mongo.getTempDir());
		WorkspaceTestCommon.stfuLoggers();
		MongoClient mongoClient = new MongoClient("localhost:" + mongo.getServerPort());
		DB db = mongoClient.getDB("GridFSBackendTest");
		gfs = new GridFS(db);
		gfsb = new GridFSBlobStore(db);
		
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(WorkspaceTestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void storetype() throws Exception {
		assertThat("correct store type", gfsb.getStoreType(), is("GridFS"));
	}
	
	@Test
	public void badInput() throws Exception {
		try {
			gfsb.saveBlob(new MD5(a32), null, true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
		
		try {
			gfsb.saveBlob(null, stringToWriteable("foo"), true);
		} catch (NullPointerException npe) {
			assertThat("correct excepction message", npe.getLocalizedMessage(),
					is("Arguments cannot be null"));
		}
	}
	
	@Test
	public void dataWithoutSortMarker() throws Exception {
		String s = "pootypoot";
		final GridFSInputFile gif = gfs.createFile(s.getBytes("UTF-8"));
		MD5 md5 = new MD5(a32);
		gif.setId(md5.getMD5());
		gif.setFilename(md5.getMD5());
		gif.save();
		
		ByteArrayFileCache d = gfsb.getBlob(md5, 
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(s));
		gfsb.removeBlob(md5);
	}
	
	@Test
	public void saveAndGetBlob() throws Exception {
		MD5 md1 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		String data = "this is a blob yo";
		gfsb.saveBlob(md1, stringToWriteable(data), true);
		MD5 md1copy = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
		ByteArrayFileCache d = gfsb.getBlob(md1copy, 
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as sorted", d.isSorted(), is(true));
		String returned = IOUtils.toString(d.getJSON());
		assertThat("Didn't get same data back from store", returned, is(data));
		assertTrue("GridFS has no external ID", gfsb.getExternalIdentifier(md1copy) == null);
		gfsb.saveBlob(md1, stringToWriteable(data), true); //should be able to save the same thing twice with no error
		
		gfsb.saveBlob(md1, stringToWriteable(data), false); //this should do nothing
		assertThat("sorted still true", gfsb.getBlob(md1copy,
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm))
					.isSorted(), is(true));
		
		MD5 md2 = new MD5("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2");
		String data2 = "this is also a blob yo";
		gfsb.saveBlob(md2, stringToWriteable(data2), false);
		d = gfsb.getBlob(md2,
				new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
		assertThat("data returned marked as unsorted", d.isSorted(), is(false));
		
		gfsb.removeBlob(md1);
		gfsb.removeBlob(md2);
	}
	
	@Test
	public void getNonExistantBlob() throws Exception {
		try {
			gfsb.getBlob(new MD5(a32),
					new ByteArrayFileCacheManager(16000000, 2000000000L, tfm));
			fail("getblob should throw exception");
		} catch (BlobStoreException wbe) {
			assertThat("wrong exception message from failed getblob",
					wbe.getLocalizedMessage(), is("Attempt to retrieve non-existant blob with chksum " + a32));
		}
	}
	
	@Test
	public void removeNonExistantBlob() throws Exception {
		gfsb.removeBlob(new MD5(a32)); //should silently not remove anything
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
