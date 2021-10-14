package us.kbase.workspace.test;

import static us.kbase.common.test.TestCommon.destroyDB;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mongodb.DB;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.WorkspaceUser;

public class WorkspaceTestCommon {
	
	public static void destroyWSandTypeDBs(DB mdb, String typedb) {
		destroyDB(mdb);
		destroyDB(mdb.getSisterDB(typedb));
	}
	
	// this data will pass typechecking for any type created in installBasicSpecs
	public static final Map<String, String> SAFE_DATA = ImmutableMap.of("thing", "foo");
	
	// types created by installBasicSpecs
	public static final TypeDefName ATYPE = new TypeDefName("SomeModule.AType");
	public static final TypeDefName ATYPE2 = new TypeDefName("SomeModule.AType2");
	public static final TypeDefId ATYPE_0_1 = new TypeDefId(ATYPE, 0, 1);
	public static final TypeDefId ATYPE_1_0 = new TypeDefId(ATYPE, 1, 0);
	public static final TypeDefId ATYPE_2_0 = new TypeDefId(ATYPE, 2, 0);
	public static final TypeDefId ATYPE2_0_1 = new TypeDefId(ATYPE2, 0, 1);
	public static final TypeDefId ATYPE2_1_0 = new TypeDefId(ATYPE2, 1, 0);
	public static final TypeDefId ATYPE2_2_0 = new TypeDefId(ATYPE2, 2, 0);
	public static final TypeDefId ATYPE2_2_1 = new TypeDefId(ATYPE2, 2, 1);
	
	public static void installBasicSpecs(final WorkspaceUser user, final Types t)
			throws Exception {
		// make a general spec that tests that don't worry about typechecking can use
		// will create versions 0.1, 1.0, and 2.0 of SomeModule.AType as well as 2.1 for
		// SomeModule.AType2
		t.requestModuleRegistration(user, "SomeModule");
		t.resolveModuleRegistration("SomeModule", true);
		t.compileNewTypeSpec(user, 
				"module SomeModule {" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing */" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				Arrays.asList("AType", "AType2"), null, null, false, null);
		t.releaseTypes(user, "SomeModule");
		t.compileNewTypeSpec(user, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"typedef structure {" +
						"string thing;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		t.releaseTypes(user, "SomeModule");
		t.compileNewTypeSpec(user, 
				"module SomeModule {" +
					"typedef structure {" +
						"string thing;" +
					"} AType;" +
					"/* @optional thing2 */" +
					"typedef structure {" +
						"string thing;" +
						"string thing2;" +
					"} AType2;" +
				"};",
				null, null, null, false, null);
		t.releaseTypes(user, "SomeModule");
	}
}
