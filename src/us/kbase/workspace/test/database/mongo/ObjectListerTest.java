package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static us.kbase.common.test.TestCommon.inst;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import us.kbase.common.test.MapBuilder;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ListObjectsParameters.Builder;
import us.kbase.workspace.database.ListObjectsParameters.ResolvedListObjectParameters;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.mongo.ObjectInfoUtils;
import us.kbase.workspace.database.mongo.ObjectLister;

public class ObjectListerTest {
	
	private static final String TYPE_3_1 = "Mod.Type-3.1";
	private static final String SHTTY_MD5 = "thisshouldbeaMD5";
	private static final ResolvedWorkspaceID WSID_1 = new ResolvedWorkspaceID(
			5, "foo", false, false);
	
	private static DBObject makeDBObject(final int id) {
		return new BasicDBObject()
				.append("ver", 7)
				.append("type", TYPE_3_1)
				.append("savedate", Date.from(inst(10000)))
				.append("savedby", "someguy")
				.append("chksum", SHTTY_MD5)
				.append("size", 3)
				.append("id", id)
				.append("ws", 5);
	}
	
	private static Map<String, Object> makeMapObject(final int id) {
		return MapBuilder.<String,Object>newHashMap()
				.with("ver", 7)
				.with("type", TYPE_3_1)
				.with("savedate", Date.from(inst(10000)))
				.with("savedby", "someguy")
				.with("chksum", SHTTY_MD5)
				.with("size", 3)
				.with("id", id)
				.with("ws", 5)
				.build();
	}
	
	private static ObjectInformation makeObjInfo(final int id) {
		return new ObjectInformation(
				id, "thing", TYPE_3_1, Date.from(inst(10000)), 7,
				new WorkspaceUser("someguy"), WSID_1, SHTTY_MD5, 3L, null);
	}
	
	// start is inclusive, end is exclusive
	private static DBObject[] makeDBObjs(final int start, final int end) {
		return IntStream.range(start, end).mapToObj(i -> makeDBObject(i)).toArray(DBObject[]::new);
	}

	private static final Map<String, Object> OBJ_MAP_1 = makeMapObject(24);
	private static final DBObject OBJ_DB_1 = makeDBObject(24);
	private static final ObjectInformation OBJ_INFO_1 = makeObjInfo(24);
	
	private final AllUsers AU = new AllUsers('*');
	
	private class Mocks {
		public final DBCollection col;
		public final ObjectInfoUtils infoutils;
		public final ObjectLister lister;

		public Mocks(DBCollection col, ObjectInfoUtils infoutils, ObjectLister lister) {
			this.col = col;
			this.infoutils = infoutils;
			this.lister = lister;
		}
	}
	
	private Mocks getMocks() {
		final DBCollection col = mock(DBCollection.class);
		final ObjectInfoUtils oiu = mock(ObjectInfoUtils.class);
		return new Mocks(col, oiu, new ObjectLister(col, oiu));
	}
	
	@Test
	public void constructFail() throws Exception {
		final Mocks m = getMocks();
		
		failConstruct(null, m.infoutils, new NullPointerException("verCol cannot be null"));
		failConstruct(m.col, null, new NullPointerException("infoUtils cannot be null"));
	}
	
	private void failConstruct(
			final DBCollection col,
			final ObjectInfoUtils oiu,
			final Exception expected) {
		try {
			new ObjectLister(col, oiu);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	private DBObject getProjection() {
		return getProjection(false);
	}
	
	private DBObject getProjection(final boolean includeMeta) {
		final BasicDBObject p = new BasicDBObject();
		Stream.of("ver", "type", "savedate", "savedby", "chksum", "size", "id", "ws")
				.forEach(s -> p.append(s, 1));
		
		if (includeMeta) {
			p.append("meta", 1);
		}
		return p;
	}
	
	@Test
	public void filterFailNull() throws Exception {
		filterFail(getMocks().lister, null, new NullPointerException("params cannot be null"));
	}
	
	@Test
	public void filterFailException() throws Exception {
		final Mocks m = getMocks();
		
		final ResolvedWorkspaceID wsid = WSID_1;
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.build()
				.resolve(PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
						.withWorkspace(wsid, Permission.READ, Permission.NONE)
						.build());
		
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		when(m.col.find(expectedQuery, getProjection()))
				.thenThrow(new MongoException("ah creahp"));
		
		filterFail(m.lister, p, new WorkspaceCommunicationException(
				"There was a problem communicating with the database"));
	}
	
	private void filterFail(
			final ObjectLister lister,
			final ResolvedListObjectParameters params,
			final Exception expected) {
		try {
			lister.filter(params);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void filterWithEmptyPermissionSet() throws Exception {
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(1)))
				.build()
				.resolve(PermissionSet.getBuilder(new WorkspaceUser("foo"), AU).build());
		
		assertThat("incorrect objs", getMocks().lister.filter(p), is(Collections.emptyList()));
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery)
			throws Exception {
		completeSimpleFilterTest(pset, p, expectedQuery, false);
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery,
			final boolean basicSort)
			throws Exception {
		completeSimpleFilterTest(pset, p, expectedQuery, basicSort, false);
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery,
			final boolean basicSort,
			final boolean boolopts)
			throws Exception {
		final BasicDBObject sort = new BasicDBObject();
		if (basicSort) {
			sort.append("ws", 1).append("id",  1).append("ver", -1);
		}
		completeSimpleFilterTest(pset, p, expectedQuery, sort, boolopts);
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery,
			final DBObject expectedSort,
			final boolean boolopts)
			throws Exception {
		final BitSet bs = new BitSet(6);
		if (boolopts) {
			bs.set(0, 6); // sets all to true
		}
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs);
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery,
			final boolean basicSort,
			final BitSet boolopts)
			throws Exception {
		final BasicDBObject sort = new BasicDBObject();
		if (basicSort) {
			sort.append("ws", 1).append("id",  1).append("ver", -1);
		}
		completeSimpleFilterTest(pset, p, expectedQuery, sort, boolopts);
	}
	
	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final DBObject expectedQuery,
			final DBObject expectedSort,
			// 0 - 5 = meta, hidden, del, only del, versions, admin
			final BitSet boolopts)
			throws Exception {
		/* Completes the test for cases where no sorts are active and all boolean parameters
		 * are false
		 */
		final Mocks m = getMocks();
		final DBCursor cur = mock(DBCursor.class);
		when(m.col.find(expectedQuery, getProjection(boolopts.get(0)))).thenReturn(cur);
		when(cur.hasNext()).thenReturn(true, true, false);
		
		// if include meta is true, all these object representations should include metadata.
		// however, the code doesn't actually know what's going on beyond setting up the
		// mongo projection, so not really worth the bother.
		when(cur.next()).thenReturn(OBJ_DB_1);
		
		when(m.infoutils.generateObjectInfo(
				pset,
				Arrays.asList(OBJ_MAP_1),
				boolopts.get(1),
				boolopts.get(2),
				boolopts.get(3),
				boolopts.get(4),
				boolopts.get(5)))
				.thenReturn(ImmutableMap.of(OBJ_MAP_1, OBJ_INFO_1));
		
		final List<ObjectInformation> ret = m.lister.filter(p);
		
		assertThat("incorrect objects", ret, is(Arrays.asList(OBJ_INFO_1)));
		
		verify(cur).sort(expectedSort);
	}
	
	@Test
	public void filterWithNoResults() throws Exception {
		final Mocks m = getMocks();
		final DBCursor cur = mock(DBCursor.class);
		
		final ResolvedWorkspaceID wsid = WSID_1;
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.build()
				.resolve(PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
						.withWorkspace(wsid, Permission.READ, Permission.NONE)
						.build());
		
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		when(m.col.find(expectedQuery, getProjection())).thenReturn(cur);
		when(cur.hasNext()).thenReturn(false);
		
		final List<ObjectInformation> ret = m.lister.filter(p);
		assertThat("incorrect objects", ret, is(Collections.emptyList()));
		
		verify(cur).sort(new BasicDBObject("ws", 1).append("id",  1).append("ver", -1));
	}
	
	@Test
	public void filterMinimal() throws Exception {
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5))).build()
				.resolve(pset);
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		completeSimpleFilterTest(pset, p, expectedQuery, true);
	}
	
	@Test
	public void filterWithAllTheFixins() throws Exception {
		// jam everything possible into the params other than startFrom and limit
		final ResolvedWorkspaceID wsi = new ResolvedWorkspaceID(2, "2", false, false);
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.withWorkspace(wsi, Permission.OWNER, Permission.READ)
				.build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(2), new WorkspaceIdentifier(5)))
				.withAfter(inst(40000))
				.withBefore(inst(80000))
				.withMinObjectID(5)
				.withMaxObjectID(78)
				.withSavers(Arrays.asList(new WorkspaceUser("a"), new WorkspaceUser("b")))
				.withType(TypeDefId.fromTypeString(TYPE_3_1))
				.withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("6", "7")))
				.withShowAllVersions(true)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.build()
				.resolve(pset);
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(2L, 5L)))
				.append("type", TYPE_3_1)
				.append("savedby", new BasicDBObject("$in", Arrays.asList("a", "b")))
				.append("$and", Arrays.asList(new BasicDBObject("meta",
						new BasicDBObject("k", "6").append("v", "7"))))
				.append("savedate",
						new BasicDBObject("$gt", Date.from(inst(40000)))
								.append("$lt", Date.from(inst(80000))))
				.append("id", new BasicDBObject("$gte", 5L).append("$lte", 78L));
		
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
	}
	
	@Test
	public void filterWithLeftSide() throws Exception {
		// Filter with the greater than filters - after and min object id
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.withAfter(inst(40000))
				.withMinObjectID(5)
				.build()
				.resolve(pset);
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)))
				.append("savedate",
						new BasicDBObject("$gt", Date.from(inst(40000))))
				.append("id", new BasicDBObject("$gte", 5L));
		
		completeSimpleFilterTest(pset, p, expectedQuery);
	}
	
	@Test
	public void filterWithRightSide() throws Exception {
		// Filter with the less than filters - before and max object id
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.withBefore(inst(80000))
				.withMaxObjectID(70)
				.build()
				.resolve(pset);
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)))
				.append("savedate",
						new BasicDBObject("$lt", Date.from(inst(80000))))
				.append("id", new BasicDBObject("$lte", 70L));
		
		completeSimpleFilterTest(pset, p, expectedQuery);
	}

	@Test
	public void basicSortSpecification() throws Exception {
		// test the various cases that affect a standard sort specification being applied to the
		// query. Does not include type based sorts.
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final Builder lop = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.withMinObjectID(7)
				.withMaxObjectID(70)
				.withLimit(800)
				.withShowAllVersions(true)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true);
		
		// case 1: basic sort is applied, unlike the following cases
		ResolvedListObjectParameters p = lop.build().resolve(pset);
		
		final DBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)))
				.append("id", new BasicDBObject("$gte", 7L).append("$lte", 70L));
		completeSimpleFilterTest(pset, p, expectedQuery, true, true);
				
		// case 2: after
		p = lop.withAfter(inst(50000)).build().resolve(pset);
		expectedQuery.put("savedate", new BasicDBObject("$gt", Date.from(inst(50000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 3: before
		p = lop.withAfter(null).withBefore(inst(90000)).build().resolve(pset);
		expectedQuery.put("savedate", new BasicDBObject("$lt", Date.from(inst(90000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 4: meta
		p = lop.withBefore(null).withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("x", "y")))
				.build().resolve(pset);
		expectedQuery.removeField("savedate");
		expectedQuery.put("$and", Arrays.asList(new BasicDBObject("meta",
				new BasicDBObject("k", "x").append("v", "y"))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 5: savers
		p = lop.withMetadata(null).withSavers(Arrays.asList(new WorkspaceUser("z")))
				.build().resolve(pset);
		expectedQuery.removeField("$and");
		expectedQuery.put("savedby", new BasicDBObject("$in", Arrays.asList("z")));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
	}
	
	@Test
	public void typeSortSpecification() throws Exception {
		// test the various cases that affect a type sort specification being applied to the
		// query. Close to the test above but trying to integrate turned into a mess.
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final Builder lop = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.withMinObjectID(7)
				.withMaxObjectID(70)
				.withLimit(800)
				.withShowAllVersions(true)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true);
		
		// case 1: a type based sort is applied, unlike the following cases
		ResolvedListObjectParameters p = lop
				.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3, 1)).build().resolve(pset);
		
		BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)))
				.append("type", TYPE_3_1)
				.append("id", new BasicDBObject("$gte", 7L).append("$lte", 70L));
		DBObject expectedSort = new BasicDBObject("type", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);
		
		// case 2: after
		p = lop.withAfter(inst(50000)).build().resolve(pset);
		expectedQuery.put("savedate", new BasicDBObject("$gt", Date.from(inst(50000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 3: before
		p = lop.withAfter(null).withBefore(inst(90000))
				.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3)).build().resolve(pset);
		expectedQuery.append("savedate", new BasicDBObject("$lt", Date.from(inst(90000))))
				.append("tymaj", "Mod.Type-3")
				.removeField("type");
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 4: meta
		p = lop.withBefore(null).withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("x", "y")))
				.withType(new TypeDefId(new TypeDefName("Mod.Type"))).build().resolve(pset);
		expectedQuery.append("$and", Arrays.asList(new BasicDBObject("meta",
				new BasicDBObject("k", "x").append("v", "y"))))
				.append("tyname", "Mod.Type")
				.removeField("savedate");
		expectedQuery.removeField("tymaj");
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
		
		// case 5: savers
		p = lop.withMetadata(null).withSavers(Arrays.asList(new WorkspaceUser("z")))
				.build().resolve(pset);
		expectedQuery.removeField("$and");
		expectedQuery.put("savedby", new BasicDBObject("$in", Arrays.asList("z")));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
	}
	
	@Test
	public void types() throws Exception {
		// test the various cases of type strings in the parameters and how that affects
		// queries and sorting.
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final Builder lop = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.withMinObjectID(7)
				.withMaxObjectID(70)
				.withLimit(800)
				.withShowAllVersions(true)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true);
		
		// case 1: full type name
		ResolvedListObjectParameters p = lop
				.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3, 1)).build().resolve(pset);
		
		BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)))
				.append("type", TYPE_3_1)
				.append("id", new BasicDBObject("$gte", 7L).append("$lte", 70L));
		DBObject expectedSort = new BasicDBObject("type", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);
		
		// case 2: major version
		p = lop.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3)).build().resolve(pset);
		expectedQuery.append("tymaj", "Mod.Type-3").removeField("type");
		// for sorts the order of insertion matters
		expectedSort = new BasicDBObject("tymaj", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);

		// case 3: no versions
		p = lop.withType(new TypeDefId(new TypeDefName("Mod.Type"))).build().resolve(pset);
		expectedQuery.append("tyname", "Mod.Type").removeField("tymaj");
		// for sorts the order of insertion matters
		expectedSort = new BasicDBObject("tyname", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);
	}
	
	@Test
	public void booleans() throws Exception {
		// test that the five individual booleans are passed to the object info instance correctly.
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final Builder lop = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)));
		
		final BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		// note that the metadata boolean is not passed to the object info instance and is
		// tested elsewhere
		
		// case 1: hidden
		ResolvedListObjectParameters p = lop.withShowHidden(true).build().resolve(pset);
		final BitSet bs = new BitSet(6);
		bs.set(1); // a fluent interface for bitset would be nice
		completeSimpleFilterTest(pset, p, expectedQuery, true, bs);
		
		// case 2: deleted
		p = lop.withShowHidden(false).withShowDeleted(true).build().resolve(pset);
		bs.clear();
		bs.set(2);
		completeSimpleFilterTest(pset, p, expectedQuery, true, bs);
		
		// case 3: only deleted
		p = lop.withShowDeleted(false).withShowOnlyDeleted(true).build().resolve(pset);
		bs.clear();
		bs.set(3);
		completeSimpleFilterTest(pset, p, expectedQuery, true, bs);
		
		// case 4: all versions
		p = lop.withShowOnlyDeleted(false).withShowAllVersions(true).build().resolve(pset);
		bs.clear();
		bs.set(4);
		completeSimpleFilterTest(pset, p, expectedQuery, true, bs);
		
		// case 5: admin
		p = lop.withShowAllVersions(false).withAsAdmin(true).build().resolve(pset);
		bs.clear();
		bs.set(5);
		completeSimpleFilterTest(pset, p, expectedQuery, true, bs);
	}
	
	@Test
	public void objectsFilteredOutByInfoUtils() throws Exception {
		// test the case where the info utils instance doesn't return entries for all
		// the input objects. This can occur when objects are filtered out because they're
		// hidden, deleted, not the most recent version, etc.
		
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5))).build().resolve(pset);
		
		final BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		final Mocks m = getMocks();
		final DBCursor cur = mock(DBCursor.class);
		when(m.col.find(expectedQuery, getProjection())).thenReturn(cur);
		when(cur.hasNext()).thenReturn(true, true, true, true, true, false);
		
		when(cur.next()).thenReturn(
				makeDBObject(7), makeDBObject(8), makeDBObject(9), makeDBObject(10));
		
		when(m.infoutils.generateObjectInfo(
				pset,
				Arrays.asList(
						makeMapObject(7), makeMapObject(8), makeMapObject(9), makeMapObject(10)),
				false,
				false,
				false,
				false,
				false))
				.thenReturn(ImmutableMap.of(
						makeMapObject(10), makeObjInfo(10),
						makeMapObject(7), makeObjInfo(7),
						makeMapObject(9), makeObjInfo(9)
				));
		
		final List<ObjectInformation> ret = m.lister.filter(p);
		
		assertThat("incorrect objects", ret, is(Arrays.asList(
				makeObjInfo(7), makeObjInfo(9), makeObjInfo(10))));
		
		verify(cur).sort(new BasicDBObject("ws", 1).append("id",  1).append("ver", -1));
	}
	
	@Test
	public void limit1() throws Exception {
		// test how the limits parameter interacts with the info utils class and output
		// these tests are going to be a bit ugly since they deal with hundreds of objects
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5))).withLimit(1).build().resolve(pset);
		
		final BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		final Mocks m = getMocks();
		final DBCursor cur = mock(DBCursor.class);
		when(m.col.find(expectedQuery, getProjection())).thenReturn(cur);
		when(cur.hasNext()).thenReturn(true);
		
		when(cur.next()).thenReturn(makeDBObject(45), makeDBObjs(46, 200));
		
		when(m.infoutils.generateObjectInfo(
				pset,
				IntStream.range(45, 145).mapToObj(i -> makeMapObject(i))
						.collect(Collectors.toList()),
				false,
				false,
				false,
				false,
				false))
				.thenReturn(ImmutableMap.of(
						makeMapObject(87), makeObjInfo(87),
						makeMapObject(72), makeObjInfo(72),
						makeMapObject(76), makeObjInfo(76)
				));
		
		final List<ObjectInformation> ret = m.lister.filter(p);
		
		assertThat("incorrect objects", ret, is(Arrays.asList(makeObjInfo(72))));
		
		verify(cur).sort(new BasicDBObject("ws", 1).append("id",  1).append("ver", -1));
	}
	
	@Test
	public void limit150() throws Exception {
		// test how the limits parameter interacts with the info utils class and output
		// these tests are going to be a bit ugly since they deal with hundreds of objects
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5))).withLimit(150).build().resolve(pset);
		
		final BasicDBObject expectedQuery = new BasicDBObject(
				"ws", new BasicDBObject("$in", Arrays.asList(5L)));
		
		final Mocks m = getMocks();
		final DBCursor cur = mock(DBCursor.class);
		when(m.col.find(expectedQuery, getProjection())).thenReturn(cur);
		when(cur.hasNext()).thenReturn(true);
		
		when(cur.next()).thenReturn(makeDBObject(40), makeDBObjs(41, 500));
		
		final Map<Map<String, Object>, ObjectInformation> retmap = new HashMap<>();
		IntStream.range(40, 80).forEach(i -> retmap.put(makeMapObject(i), makeObjInfo(i)));
		// oh dang, we're going to have to do two passes
		IntStream.range(90, 190).forEach(i -> retmap.put(makeMapObject(i), makeObjInfo(i)));
		
		when(m.infoutils.generateObjectInfo(
				pset,
				IntStream.range(40, 190).mapToObj(i -> makeMapObject(i))
						.collect(Collectors.toList()),
				false,
				false,
				false,
				false,
				false))
				.thenReturn(retmap);
		
		// don't reuse retmap, will screw up the mocks
		final Map<Map<String, Object>, ObjectInformation> retmap2 = new HashMap<>();
		IntStream.range(190, 340).forEach(i -> retmap2.put(makeMapObject(i), makeObjInfo(i)));
		
		when(m.infoutils.generateObjectInfo(
				pset,
				IntStream.range(190, 340).mapToObj(i -> makeMapObject(i))
						.collect(Collectors.toList()),
				false,
				false,
				false,
				false,
				false))
				.thenReturn(retmap2);
		
		final List<ObjectInformation> ret = m.lister.filter(p);
		
		final List<ObjectInformation> expected = new LinkedList<>();
		IntStream.range(40, 80).forEach(i -> expected.add(makeObjInfo(i)));
		IntStream.range(90, 200).forEach(i -> expected.add(makeObjInfo(i)));
		
		assertThat("incorrect objects", ret, is(expected));
		
		verify(cur).sort(new BasicDBObject("ws", 1).append("id",  1).append("ver", -1));
	}

}
