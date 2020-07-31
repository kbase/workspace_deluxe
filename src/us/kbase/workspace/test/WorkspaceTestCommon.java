package us.kbase.workspace.test;

import static us.kbase.common.test.TestCommon.destroyDB;

import com.mongodb.DB;

public class WorkspaceTestCommon {
	
	public static void destroyWSandTypeDBs(DB mdb, String typedb) {
		destroyDB(mdb);
		destroyDB(mdb.getSisterDB(typedb));
	}
}
