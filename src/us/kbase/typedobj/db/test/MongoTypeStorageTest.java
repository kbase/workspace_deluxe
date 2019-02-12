package us.kbase.typedobj.db.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.MongoClient;

import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.typedobj.db.ModuleInfo;
import us.kbase.typedobj.db.MongoTypeStorage;
import us.kbase.typedobj.exceptions.TypeStorageException;

// most tests are in TypeRegisteringTest. These are mongo-specific.

//TODO TEST add more test as needed.

public class MongoTypeStorageTest {

	private static MongoController MONGO;
	private static DB MONGO_DB;
	
	@BeforeClass
	public static void setUp() throws Exception {
		MONGO = new MongoController(TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using mongo temp dir " + MONGO.getTempDir());
		
		System.out.println("Started test mongo instance at localhost:" +
				MONGO.getServerPort());
		
		final MongoClient mc = new MongoClient("localhost:" + MONGO.getServerPort());
		MONGO_DB = mc.getDB("test_" + MongoTypeStorageTest.class.getSimpleName());
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (MONGO != null) {
			System.out.println("destroying mongo temp files");
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		TestCommon.destroyDB(MONGO_DB);
	}
	
	@Test
	public void getModuleSupportedStateFailNoModule() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		final ModuleInfo mi = new ModuleInfo();
		mi.setModuleName("name");
		mi.setVersionTime(10000);
		mi.setReleased(false);
		
		mts.writeModuleRecords(mi, "{};", 12000);
		
		assertThat("incorrect get supported state", mts.getModuleSupportedState("name"), is(true));
		
		try {
			mts.getModuleSupportedState("name2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Support information is unavailable for module: name2"));
		}
	}
	
	@Test
	public void getFuncParseRecordFailNoRecord() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		mts.writeFuncParseRecord("funcMod", "funcN", "ver", 567, "funchere");
		
		assertThat("incorrect func parse", mts.getFuncParseRecord("funcMod", "funcN", "ver"),
				is("funchere"));
		
		try {
			mts.getFuncParseRecord("funcMod", "funcN", "ver2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Function parse record was not found for funcMod.funcN.ver2"));
		}
	}
}
