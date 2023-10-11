package us.kbase.workspace.test.database.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import org.bson.Document;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import us.kbase.common.test.MapBuilder;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
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
import us.kbase.workspace.database.RefLimit;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.mongo.ObjectInfoUtils;
import us.kbase.workspace.database.mongo.ObjectLister;

public class ObjectListerTest {

	private static final String TYPE_3_2 = "Mod.Type-3.2";
	private static final String SHTTY_MD5 = "thisshouldbeaMD5";
	private static final ResolvedWorkspaceID WSID_1 = new ResolvedWorkspaceID(
			5, "foo", false, false);

	private static Document makeDBObject(final int id) {
		return new Document()
				.append("ver", 7)
				.append("tyname", "Mod.Type")
				.append("tymaj", 3)
				.append("tymin", 2)
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
				.with("tyname", "Mod.Type")
				.with("tymaj", 3)
				.with("tymin", 2)
				.with("savedate", Date.from(inst(10000)))
				.with("savedby", "someguy")
				.with("chksum", SHTTY_MD5)
				.with("size", 3)
				.with("id", id)
				.with("ws", 5)
				.build();
	}

	private static ObjectInformation makeObjInfo(final int id) {
		return ObjectInformation.getBuilder()
				.withObjectID(id)
				.withObjectName("thing")
				.withType(AbsoluteTypeDefId.fromAbsoluteTypeString(TYPE_3_2))
				.withSavedDate(inst(10000))
				.withVersion(7)
				.withSavedBy(new WorkspaceUser("someguy"))
				.withWorkspace(WSID_1)
				.withChecksum(SHTTY_MD5)
				.withSize(3)
				.build();
	}

	// start is inclusive, end is exclusive
	private static Document[] makeDBObjs(final int start, final int end) {
		return IntStream.range(start, end).mapToObj(i -> makeDBObject(i)).toArray(Document[]::new);
	}

	private static final Map<String, Object> OBJ_MAP_1 = makeMapObject(24);
	private static final Document OBJ_DB_1 = makeDBObject(24);
	private static final ObjectInformation OBJ_INFO_1 = makeObjInfo(24);

	private final AllUsers AU = new AllUsers('*');

	private class Mocks {
		public final MongoCollection<Document> col;
		public final ObjectInfoUtils infoutils;
		public final ObjectLister lister;
		public final FindIterable<Document> cur;
		public final MongoCursor<Document> mcur;

		public Mocks() {
			@SuppressWarnings("unchecked")
			final MongoCollection<Document> col = mock(MongoCollection.class);
			this.col = col;
			infoutils = mock(ObjectInfoUtils.class);
			lister = new ObjectLister(col, infoutils);
			@SuppressWarnings("unchecked")
			final FindIterable<Document> cur = mock(FindIterable.class);
			this.cur = cur;
			@SuppressWarnings("unchecked")
			final MongoCursor<Document> mcur = mock(MongoCursor.class);
			this.mcur = mcur;
		}
	}

	@Test
	public void constructFail() throws Exception {
		final Mocks m = new Mocks();

		failConstruct(null, m.infoutils, new NullPointerException("verCol cannot be null"));
		failConstruct(m.col, null, new NullPointerException("infoUtils cannot be null"));
	}

	private void failConstruct(
			final MongoCollection<Document> col,
			final ObjectInfoUtils oiu,
			final Exception expected) {
		try {
			new ObjectLister(col, oiu);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}

	private Document getProjection() {
		return getProjection(false);
	}

	private Document getProjection(final boolean includeMeta) {
		final Document p = new Document();
		Stream.of("ver", "tyname", "tymaj", "tymin", "savedate", "savedby", "chksum", "size",
				"id", "ws")
				.forEach(s -> p.append(s, 1));

		if (includeMeta) {
			p.append("meta", 1);
		}
		return p;
	}

	@Test
	public void filterFailNull() throws Exception {
		filterFail(new Mocks().lister, null, new NullPointerException("params cannot be null"));
	}

	@Test
	public void filterFailException() throws Exception {
		final Mocks m = new Mocks();

		final ResolvedWorkspaceID wsid = WSID_1;
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.build()
				.resolve(PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
						.withWorkspace(wsid, Permission.READ, Permission.NONE)
						.build());


		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

		when(m.col.find(expectedQuery)).thenThrow(new MongoException("ah creahp"));

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

		assertThat("incorrect objs", new Mocks().lister.filter(p), is(Collections.emptyList()));
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery)
			throws Exception {
		completeSimpleFilterTest(pset, p, expectedQuery, false);
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery,
			final boolean basicSort)
			throws Exception {
		completeSimpleFilterTest(pset, p, expectedQuery, basicSort, false);
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery,
			final boolean basicSort,
			final boolean boolopts)
			throws Exception {
		final Document sort = new Document();
		if (basicSort) {
			sort.append("ws", 1).append("id",  1).append("ver", -1);
		}
		completeSimpleFilterTest(pset, p, expectedQuery, sort, boolopts);
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery,
			final Document expectedSort,
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
			final Document expectedQuery,
			final boolean basicSort,
			final BitSet boolopts)
			throws Exception {
		final Document sort = new Document();
		if (basicSort) {
			sort.append("ws", 1).append("id",  1).append("ver", -1);
		}
		completeSimpleFilterTest(pset, p, expectedQuery, sort, boolopts);
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery,
			final Document expectedSort,
			// 0 - 5 = meta, hidden, del, only del, versions, admin
			final BitSet boolopts)
			throws Exception {
		completeSimpleFilterTest(
				pset, p, expectedQuery, expectedSort, boolopts, new Document());
	}

	private void completeSimpleFilterTest(
			final PermissionSet pset,
			final ResolvedListObjectParameters p,
			final Document expectedQuery,
			final Document expectedSort,
			// 0 - 5 = meta, hidden, del, only del, versions, admin
			final BitSet boolopts,
			final Document startFrom)
			throws Exception {
		final Mocks m = new Mocks();
		when(m.col.find(expectedQuery)).thenReturn(m.cur);
		when(m.cur.projection(getProjection(boolopts.get(0)))).thenReturn(m.cur);
		when(m.cur.iterator()).thenReturn(m.mcur);
		when(m.cur.hint(expectedSort)).thenReturn(m.cur); // mock fluent interface
		when(m.mcur.hasNext()).thenReturn(true, true, false);

		// if include meta is true, all these object representations should include metadata.
		// however, the code doesn't actually know what's going on beyond setting up the
		// mongo projection, so not really worth the bother.
		when(m.mcur.next()).thenReturn(OBJ_DB_1);

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

		if (!startFrom.keySet().isEmpty()) {
			verify(m.cur).min(startFrom);
		} else {
			verify(m.cur, never()).hint(any(Document.class));
			verify(m.cur, never()).min(any(Document.class));
		}
		verify(m.cur).sort(expectedSort);
	}

	@Test
	public void filterWithNoResults() throws Exception {
		final Mocks m = new Mocks();

		final ResolvedWorkspaceID wsid = WSID_1;
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)))
				.build()
				.resolve(PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
						.withWorkspace(wsid, Permission.READ, Permission.NONE)
						.build());


		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

		when(m.col.find(expectedQuery)).thenReturn(m.cur);
		when(m.cur.projection(getProjection())).thenReturn(m.cur);
		when(m.cur.iterator()).thenReturn(m.mcur);
		when(m.mcur.hasNext()).thenReturn(false);

		final List<ObjectInformation> ret = m.lister.filter(p);
		assertThat("incorrect objects", ret, is(Collections.emptyList()));

		verify(m.cur).sort(new Document("ws", 1).append("id",  1).append("ver", -1));
	}

	@Test
	public void filterMinimal() throws Exception {
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5))).build()
				.resolve(pset);

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

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
				.withType(TypeDefId.fromTypeString(TYPE_3_2))
				.withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("6", "7")))
				.withShowAllVersions(true)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.build()
				.resolve(pset);

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(2L, 5L)))
				.append("tyname", "Mod.Type").append("tymaj", 3).append("tymin", 2)
				.append("savedby", new Document("$in", Arrays.asList("a", "b")))
				.append("$and", Arrays.asList(new Document("meta",
						new Document("k", "6").append("v", "7"))))
				.append("savedate",
						new Document("$gt", Date.from(inst(40000)))
								.append("$lt", Date.from(inst(80000))))
				.append("id", new Document("$gte", 5L).append("$lte", 78L));

		completeSimpleFilterTest(pset, p, expectedQuery, false, true);
	}

	@Test
	public void filterWithStartFrom() throws Exception {
		// test filtering starting from a reference
		final BitSet bs = new BitSet(); // all false
		final PermissionSet pset = PermissionSet.getBuilder(new WorkspaceUser("foo"), AU)
				.withWorkspace(WSID_1, Permission.READ, Permission.NONE)
				.build();
		final Builder lob = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(5)));

		// case 1: no start from, no type
		ResolvedListObjectParameters p = lob.build().resolve(pset);

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));
		Document expectedSort = new Document("ws", 1).append("id", 1).append("ver", -1);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, new Document());

		// case 2: start from, wsid only
		p = lob.withStartFrom(RefLimit.build(3L, null, null)).build().resolve(pset);
		Document expectedMin = new Document("ws", 3L).append("id", 1L)
				.append("ver", Integer.MAX_VALUE);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

		// case 3: start from, wsid & objid
		p = lob.withStartFrom(RefLimit.build(24L, 108L, null)).build().resolve(pset);
		expectedMin = new Document("ws", 24L).append("id", 108L)
				.append("ver", Integer.MAX_VALUE);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

		// case 4: start from fully specified
		p = lob.withStartFrom(RefLimit.build(24L, 108L, 7)).build().resolve(pset);
		expectedMin = new Document("ws", 24L).append("id", 108L).append("ver", 7);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

		// case 5: start from wsid only and a full type
		p = lob.withStartFrom(RefLimit.build(32L, null, null))
				.withType(TypeDefId.fromTypeString(TYPE_3_2)).build().resolve(pset);
		expectedQuery.append("tyname", "Mod.Type").append("tymaj", 3).append("tymin", 2);
		expectedSort = new Document("tyname", 1).append("tymaj", 1).append("tymin", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		expectedMin = new Document("tyname", "Mod.Type").append("tymaj", 3).append("tymin", 2)
				.append("ws", 32L).append("id", 1L).append("ver", Integer.MAX_VALUE);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

		// case 6: start from wsid & objid and a major version type
		p = lob.withStartFrom(RefLimit.build(1L, 1L, null))
				.withType(TypeDefId.fromTypeString("Mod.Type-3")).build().resolve(pset);
		expectedQuery.remove("tymin");
		expectedSort = new Document("tyname", 1).append("tymaj", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		expectedMin = new Document("tyname", "Mod.Type").append("tymaj", 3)
				.append("ws", 1L).append("id", 1L).append("ver", Integer.MAX_VALUE);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

		// case 7: start from fully specified and a name only type
		p = lob.withStartFrom(RefLimit.build(64L, 128L, 256))
				.withType(TypeDefId.fromTypeString("Mod.Type")).build().resolve(pset);
		expectedQuery.remove("tymaj");
		expectedSort = new Document("tyname", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		expectedMin = new Document("tyname", "Mod.Type")
				.append("ws", 64L).append("id", 128L).append("ver", 256);

		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, bs, expectedMin);

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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)))
				.append("savedate",
						new Document("$gt", Date.from(inst(40000))))
				.append("id", new Document("$gte", 5L));

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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)))
				.append("savedate",
						new Document("$lt", Date.from(inst(80000))))
				.append("id", new Document("$lte", 70L));

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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)))
				.append("id", new Document("$gte", 7L).append("$lte", 70L));
		completeSimpleFilterTest(pset, p, expectedQuery, true, true);

		// case 2: after
		p = lop.withAfter(inst(50000)).build().resolve(pset);
		expectedQuery.put("savedate", new Document("$gt", Date.from(inst(50000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 3: before
		p = lop.withAfter(null).withBefore(inst(90000)).build().resolve(pset);
		expectedQuery.put("savedate", new Document("$lt", Date.from(inst(90000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 4: meta
		p = lop.withBefore(null).withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("x", "y")))
				.build().resolve(pset);
		expectedQuery.remove("savedate");
		expectedQuery.put("$and", Arrays.asList(new Document("meta",
				new Document("k", "x").append("v", "y"))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 5: savers
		p = lop.withMetadata(null).withSavers(Arrays.asList(new WorkspaceUser("z")))
				.build().resolve(pset);
		expectedQuery.remove("$and");
		expectedQuery.put("savedby", new Document("$in", Arrays.asList("z")));
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
				.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3, 2)).build().resolve(pset);

		Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)))
				.append("tyname", "Mod.Type").append("tymaj", 3).append("tymin", 2)
				.append("id", new Document("$gte", 7L).append("$lte", 70L));
		Document expectedSort = new Document("tyname", 1)
				.append("tymaj", 1).append("tymin", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);

		// case 2: after
		p = lop.withAfter(inst(50000)).build().resolve(pset);
		expectedQuery.put("savedate", new Document("$gt", Date.from(inst(50000))));
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 3: before
		p = lop.withAfter(null).withBefore(inst(90000))
				.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3)).build().resolve(pset);
		expectedQuery.append("savedate", new Document("$lt", Date.from(inst(90000))))
				.remove("tymin");
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 4: meta
		p = lop.withBefore(null).withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("x", "y")))
				.withType(new TypeDefId(new TypeDefName("Mod.Type"))).build().resolve(pset);
		expectedQuery.append("$and", Arrays.asList(new Document("meta",
				new Document("k", "x").append("v", "y"))))
				.append("tyname", "Mod.Type")
				.remove("savedate");
		expectedQuery.remove("tymaj");
		completeSimpleFilterTest(pset, p, expectedQuery, false, true);

		// case 5: savers
		p = lop.withMetadata(null).withSavers(Arrays.asList(new WorkspaceUser("z")))
				.build().resolve(pset);
		expectedQuery.remove("$and");
		expectedQuery.put("savedby", new Document("$in", Arrays.asList("z")));
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

		Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)))
				.append("tyname", "Mod.Type").append("tymaj", 3).append("tymin", 1)
				.append("id", new Document("$gte", 7L).append("$lte", 70L));
		Document expectedSort = new Document("tyname", 1)
				.append("tymaj", 1).append("tymin", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);

		// case 2: major version
		p = lop.withType(new TypeDefId(new TypeDefName("Mod.Type"), 3)).build().resolve(pset);
		expectedQuery.remove("tymin");
		// for sorts the order of insertion matters
		expectedSort = new Document("tyname", 1).append("tymaj", 1)
				.append("ws", 1).append("id", 1).append("ver", -1);
		completeSimpleFilterTest(pset, p, expectedQuery, expectedSort, true);

		// case 3: no versions
		p = lop.withType(new TypeDefId(new TypeDefName("Mod.Type"))).build().resolve(pset);
		expectedQuery.append("tyname", "Mod.Type").remove("tymaj");
		// for sorts the order of insertion matters
		expectedSort = new Document("tyname", 1)
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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

		final Mocks m = new Mocks();
		when(m.col.find(expectedQuery)).thenReturn(m.cur);
		when(m.cur.projection(getProjection())).thenReturn(m.cur);
		when(m.cur.iterator()).thenReturn(m.mcur);

		when(m.mcur.hasNext()).thenReturn(true, true, true, true, true, false);
		when(m.mcur.next()).thenReturn(
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

		verify(m.cur).sort(new Document("ws", 1).append("id",  1).append("ver", -1));
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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

		final Mocks m = new Mocks();
		when(m.col.find(expectedQuery)).thenReturn(m.cur);
		when(m.cur.projection(getProjection())).thenReturn(m.cur);
		when(m.cur.iterator()).thenReturn(m.mcur);

		when(m.mcur.hasNext()).thenReturn(true);
		when(m.mcur.next()).thenReturn(makeDBObject(45), makeDBObjs(46, 200));

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

		verify(m.cur).sort(new Document("ws", 1).append("id",  1).append("ver", -1));
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

		final Document expectedQuery = new Document(
				"ws", new Document("$in", Arrays.asList(5L)));

		final Mocks m = new Mocks();

		when(m.col.find(expectedQuery)).thenReturn(m.cur);
		when(m.cur.projection(getProjection())).thenReturn(m.cur);
		when(m.cur.iterator()).thenReturn(m.mcur);
		when(m.mcur.hasNext()).thenReturn(true);
		when(m.mcur.next()).thenReturn(makeDBObject(40), makeDBObjs(41, 500));

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

		verify(m.cur).sort(new Document("ws", 1).append("id",  1).append("ver", -1));
	}
}
