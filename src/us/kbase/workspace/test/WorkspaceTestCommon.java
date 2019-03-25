package us.kbase.workspace.test;

import static us.kbase.common.test.TestCommon.getTestProperty;
import static us.kbase.common.test.TestCommon.destroyDB;

import com.mongodb.DB;

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
	
	public static void destroyWSandTypeDBs(DB mdb, String typedb) {
		destroyDB(mdb);
		destroyDB(mdb.getSisterDB(typedb));
	}
}
