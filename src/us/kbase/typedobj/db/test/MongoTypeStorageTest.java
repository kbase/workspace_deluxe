package us.kbase.typedobj.db.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.List;

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
import us.kbase.typedobj.db.OwnerInfo;
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
	
	@Test
	public void getTypeParseRecordFailNoRecord() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		mts.writeTypeParseRecord("typeMod", "typeN", "v", 797, "typeDoc");
		
		assertThat("incorrect type parse", mts.getTypeParseRecord("typeMod", "typeN", "v"),
				is("typeDoc"));
		
		try {
			mts.getTypeParseRecord("typeMod", "typeN", "v2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Type parse record was not found for typeMod.typeN.v2"));
		}
	}
	
	@Test
	public void getTypeSchemaRecordFailNoRecord() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		mts.writeTypeSchemaRecord("mN", "tN", "v", 123L, "doc", "md5");
		
		
		assertThat("incorrect type parse", mts.getTypeSchemaRecord("mN", "tN", "v"), is("doc"));
		
		try {
			mts.getTypeSchemaRecord("mN", "tN", "v2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Type schema record was not found for mN.tN.v2"));
		}
	}
	
	@Test
	public void getTypeMd5FailNoRecord() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		mts.writeTypeSchemaRecord("mN", "tN", "v", 123L, "doc", "md5");
		
		
		assertThat("incorrect type parse", mts.getTypeMd5("mN", "tN", "v"), is("md5"));
		
		try {
			mts.getTypeMd5("mN", "tN", "v2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Type schema record was not found for mN.tN.v2"));
		}
	}
	
	@Test
	public void addNewModuleRegistration() throws Exception {
		// just a basic test to make sure it works.
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		final ModuleInfo mi = new ModuleInfo();
		mi.setModuleName("mod");
		mi.setReleased(true);
		mi.setUploadUserId("u");
		
		mts.writeModuleRecords(mi, "{};", 10000L);
		
		mts.addNewModuleRegistrationRequest("modN", "u");
		
		final List<OwnerInfo> loi = mts.getNewModuleRegistrationRequests();
		
		assertThat("incorrect request count", loi.size(), is(1));
		final OwnerInfo oi = loi.get(0);
		assertThat("incorrect module", oi.getModuleName(), is("modN"));
		assertThat("incorrect owner", oi.getOwnerUserId(), is("u"));
		assertThat("incorrect priv", oi.isWithChangeOwnersPrivilege(), is(true));
	}
	
	@Test
	public void addNewModuleRegistrationFailMaxRequests() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		for (int i = 1; i < 31; i++) {
			mts.addNewModuleRegistrationRequest("m" + i, "u");
		}
		
		try {
			mts.addNewModuleRegistrationRequest("m31", "u");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"User u has maximal count of requests: 30"));
		}
	}
	
	@Test
	public void addNewModuleRegistrationFailRequestExists() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		mts.addNewModuleRegistrationRequest("mod", "u");
		
		try {
			mts.addNewModuleRegistrationRequest("mod", "u2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Registration of module mod was already requested"));
		}
	}
	
	@Test
	public void addNewModuleRegistrationFailModuleExists() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		final ModuleInfo mi = new ModuleInfo();
		mi.setModuleName("mod");
		mi.setReleased(true);
		mi.setUploadUserId("u");
		mts.writeModuleRecords(mi, "{};", 10000L);
		
		try {
			mts.addNewModuleRegistrationRequest("mod", "u2");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Module mod was already registered"));
		}
	}
	
	@Test
	public void setModuleReleaseVersionFailNoModule() throws Exception {
		final MongoTypeStorage mts = new MongoTypeStorage(MONGO_DB);
		
		final ModuleInfo mi = new ModuleInfo();
		mi.setModuleName("mod");
		mi.setReleased(true);
		mi.setUploadUserId("u");
		mts.writeModuleRecords(mi, "{};", 10000L);
		
		try {
			mts.setModuleReleaseVersion("mod2", 20000L);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypeStorageException(
					"Module mod2 was not registered"));
		}
		
	}
}
