package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jongo.Jongo;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.DefaultReferenceParser;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.mongo.MongoWorkspaceDB;
import us.kbase.workspace.kbase.Util;
import us.kbase.workspace.test.WorkspaceTestCommon;
import us.kbase.workspace.workspaces.WorkspaceSaveObject;
import us.kbase.workspace.workspaces.Workspaces;

import com.mongodb.DB;

public class TestMongoInternals {
	
	private static DB db;
	private static Jongo jdb;
	private static MongoWorkspaceDB mwdb;
	private static Workspaces ws;
	
	public static final TypeDefId SAFE_TYPE =
			new TypeDefId(new TypeDefName("SomeModule", "AType"), 0, 1);

	@BeforeClass
	public static void setUpClass() throws Exception {
		db = WorkspaceTestCommon.destroyAndSetupDB(1, "gridFS", null);
		jdb = new Jongo(db);
		String host = WorkspaceTestCommon.getHost();
		String mUser = WorkspaceTestCommon.getMongoUser();
		String mPwd = WorkspaceTestCommon.getMongoPwd();
		String db1 = WorkspaceTestCommon.getDB1();
		final String kidlpath = new Util().getKIDLpath();
		
		if (mUser != null) {
			mwdb = new MongoWorkspaceDB(host, db1, "fOo", mUser, mPwd,
					kidlpath, null);
		} else {
			mwdb = new MongoWorkspaceDB(host, db1, "foo", "foo", "foo",
					kidlpath, null);
		}
		ws = new Workspaces(mwdb, new DefaultReferenceParser());
		assertTrue("GridFS backend setup failed", ws.getBackendType().equals("GridFS"));

		//make a general spec that tests that don't worry about typechecking can use
		WorkspaceUser foo = new WorkspaceUser("foo");
		//simple spec
		ws.requestModuleRegistration(foo, "SomeModule");
		ws.resolveModuleRegistration("SomeModule", true);
		ws.compileNewTypeSpec(foo, 
				"module SomeModule {/* @optional thing */ typedef structure {string thing;} AType;};",
				Arrays.asList("AType"), null, null, false, null);
		ws.releaseTypes(foo, "SomeModule");
	}
	
	@Test
	public void refCounting() throws Exception {
		final String refcntspec =
				"module RefCount {" +
					"/* @id ws */" +
					"typedef string reference;" +
					"/* @optional ref */" + 
					"typedef structure {" +
						"reference ref;" +
					"} RefType;" +
				"};";
		
		String mod = "RefCount";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, refcntspec, Arrays.asList("RefType"), null, null, false, null);
		TypeDefId refcounttype = new TypeDefId(new TypeDefName(mod, "RefType"), 0, 1);
		
		WorkspaceIdentifier wspace = new WorkspaceIdentifier("refcount");
		long wsid = ws.createWorkspace(userfoo, wspace.getName(), false, null).getId();
		Provenance emptyprov = new Provenance(userfoo);
		Map<String, Object> data1 = new HashMap<String, Object>();
		data1.put("foo", 3);
		
		for (int i = 1; i < 5; i++) {
			for (int j = 0; j < 4; j++) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(new ObjectIDNoWSNoVer("obj" + i),
								data1, SAFE_TYPE, null, emptyprov, false)));
			}
		}
		// now we've got a 4x4 set of objects
		
		int[][] expected = new int[5][5];
		
		for (int i = 0; i < 16; i++) {
			int obj = (int) (Math.random() * 4.0) + 1;
			int ver = (int) (Math.random() * 4.0) + 1;
			expected[obj][ver]++;
			if (i % 2 == 0) {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(withRef(data1, wsid, "obj" + obj, ver),
						refcounttype, null, emptyprov, false)));
			} else {
				ws.saveObjects(userfoo, wspace, Arrays.asList(
						new WorkspaceSaveObject(withRef(data1, wsid, obj, ver),
						refcounttype, null, emptyprov, false)));
			}
		}
		
		for (int i = 1; i < 5; i++) {
			@SuppressWarnings("unchecked")
			Map<String, Object> obj = jdb.getCollection("workspaceObjects")
					.findOne("{ws: #, id: #}", wsid, i).as(Map.class);
			@SuppressWarnings("unchecked")
			List<Integer> refcnts = (List<Integer>) obj.get("refcnt");
			for (int j = 0; j < 4; j++) {
				assertThat("correct ref count", refcnts.get(j), is(expected[i][j + 1]));
			}
		}
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			int name, int ver) {
		return withRef(map, wsid, "" + name, ver);
	}
	
	private Map<String, Object> withRef(Map<String, Object> map, long wsid,
			String name, int ver) {
		map.put("ref", wsid + "/" + name + "/" + ver);
		return map;
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void subdata() throws Exception {
		final String specSubdata =
				"module TestSubdata {\n" +
					"typedef structure {\n" +
						"int legs\n;" +
						"string patronymic;\n" +
						"int charisma;\n" +
					"} BugType;\n" +
					"\n" +
					"/* @searchable ws_subset booger bugs.legs\n" +
					"   @searchable ws_subset whanga.*.(legs,charisma)\n" +
					"   @searchable ws_subset foop.[*].(legs,patronymic)\n" +
					"   @searchable ws_subset keys_of(looloo)\n" +
					" */" +
					"typedef structure {" +
						"int booger;" +
						"int booger1;" +
						"BugType bugs;" +
						"BugType bugs1;" +
						"mapping<string, BugType> whanga;" +
						"mapping<string, BugType> whanga1;" +
						"list<BugType> foop;" +
						"list<BugType> foop1;" +
						"mapping<string, string> looloo;" +
						"mapping<string, string> looloo1;" +
					"} SubSetType;" +
				"};";
		String mod = "TestSubdata";
		WorkspaceUser userfoo = new WorkspaceUser("foo");
		ws.requestModuleRegistration(userfoo, mod);
		ws.resolveModuleRegistration(mod, true);
		ws.compileNewTypeSpec(userfoo, specSubdata, Arrays.asList("SubSetType"), null, null, false, null);
		TypeDefId subsettype = new TypeDefId(new TypeDefName(mod, "SubSetType"), 0, 1);
		
		WorkspaceIdentifier subdataws = new WorkspaceIdentifier("subset");
		ws.createWorkspace(userfoo, subdataws.getName(), false, null);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("booger", 6);
		Map<String, Object> bugs = new HashMap<String, Object>();
		bugs.put("legs", 3);
		bugs.put("patronymic", "pater");
		bugs.put("charisma", 18);
		data.put("bugs", bugs);
		Map<String, Object> whangabugs = new HashMap<String, Object>(bugs);
		Map<String, Map<String, Object>> whanga =
				new HashMap<String, Map<String, Object>>();
		whanga.put("foo", whangabugs);
		whanga.put("bar", whangabugs);
		data.put("whanga", whanga);
		Map<String, Object> foopbugs = new HashMap<String, Object>(bugs);
		data.put("foop", Arrays.asList(foopbugs, foopbugs, foopbugs));
		Map<String, String> looloo = new HashMap<String, String>();
		looloo.put("question", "42");
		looloo.put("answer", "?");
		data.put("looloo", looloo);
		
		//need to remove stuff from whanga and foop
		Map<String, Object> expected = new HashMap<String, Object>(data);
		
		data.put("booger1", 7);
		Map<String, Object> bugs1 = new HashMap<String, Object>();
		bugs1.put("legs", 2);
		bugs1.put("patronymic", "mater");
		bugs1.put("charisma", 3);
		data.put("bugs1", bugs1);
		Map<String, Map<String, Object>> whanga1 =
				new HashMap<String, Map<String, Object>>();
		whanga1.put("foo1", bugs1);
		whanga1.put("bar1", bugs1);
		data.put("whanga1", whanga1);
		data.put("foop1", Arrays.asList(bugs1, bugs1, bugs1));
		Map<String, String> looloo1 = new HashMap<String, String>();
		looloo1.put("question1", "43");
		looloo1.put("answer1", "!");
		data.put("looloo1", looloo1);
		
		ws.saveObjects(userfoo, subdataws, Arrays.asList(
				new WorkspaceSaveObject(data, subsettype, null, new Provenance(userfoo), false)));
		
		((Map<String, Object>) expected.get("bugs")).remove("patronymic");
		((Map<String, Object>) expected.get("bugs")).remove("charisma");
		for (Entry<String, Map<String, Object>> entry:
			((Map<String, Map<String, Object>>) expected.get("whanga")).entrySet()) {
			entry.getValue().remove("patronymic");
		}
		for (Map<String, Object> foop: (List<Map<String, Object>>) expected.get("foop")) {
			foop.remove("charisma");
		}
		expected.put("looloo", new HashSet<String>(Arrays.asList("question", "answer")));
		
		ResolvedWorkspaceID rwi = mwdb.resolveWorkspace(subdataws);
		ObjectIDResolvedWS oid = new ObjectIDResolvedWS(rwi, 1L);
		Map<String, Object> d = mwdb.getObjectSubData(new HashSet<ObjectIDResolvedWS>(Arrays.asList(
				new ObjectIDResolvedWS(rwi, 1L)))).get(oid);
						
		d.put("looloo", new HashSet<String>((List<String>) d.get("looloo")));
		assertThat("subdata is not correct", expected, is(d));
	}
}
