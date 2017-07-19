package us.kbase.workspace.test;

import static us.kbase.common.test.TestCommon.getTestProperty;
import static us.kbase.common.test.TestCommon.destroyDB;

import java.net.URL;

import us.kbase.common.test.TestException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;

public class WorkspaceTestCommon {
	
	public static final String PLACKUPEXE = "test.plackup.exe";
	public static final String HANDLE_SRV_PSGI = "test.handle.service.psgi";
	public static final String HANDLE_MGR_PSGI = "test.handle.manager.psgi";
	public static final String HANDLE_PERL5LIB = "test.handle.PERL5LIB";
	
	public static final String GRIDFS = "gridFS";
	public static final String SHOCK = "shock";
			
	public static String getPlackupExe() {
		return getTestProperty(PLACKUPEXE);
	}
	
	public static String getHandleServicePSGI() {
		return getTestProperty(HANDLE_SRV_PSGI);
	}
	
	public static String getHandleManagerPSGI() {
		return getTestProperty(HANDLE_MGR_PSGI);
	}
	
	public static String getHandlePERL5LIB() {
		return getTestProperty(HANDLE_PERL5LIB);
	}
	
	//useful for tests starting a server with GFS as the backend
	public static void initializeGridFSWorkspaceDB(DB mdb, String typedb) {
		destroyWSandTypeDBs(mdb, typedb);
		DBObject dbo = new BasicDBObject();
		dbo.put("type_db", typedb);
		dbo.put("backend", GRIDFS);
		mdb.getCollection("settings").insert(dbo);
		System.out.println(String.format("Configured new %s backend.", GRIDFS));
	}

	public static void destroyWSandTypeDBs(DB mdb, String typedb) {
		destroyDB(mdb);
		destroyDB(mdb.getSisterDB(typedb));
	}
	
	//useful for tests starting a server with shock as the backend
	public static void initializeShockWorkspaceDB(DB mdb, String shockuser,
			URL shockURL, String typedb) {
		destroyWSandTypeDBs(mdb, typedb);
		DBObject dbo = new BasicDBObject();
		dbo.put("type_db", typedb);
		dbo.put("backend", SHOCK);
		if (shockuser == null) {
			throw new TestException("Shock user cannot be null");
		}
		dbo.put("shock_user", shockuser);
		if (shockURL == null) {
			throw new TestException("The shock url may not be null");
		}
		dbo.put("shock_location", shockURL.toExternalForm());
		System.out.println(String.format(
				"Setting up shock with user %s and url %s", shockuser,
				shockURL.toExternalForm()));
	mdb.getCollection("settings").insert(dbo);
		System.out.println(String.format("Configured new %s backend.", SHOCK));
	}

}
