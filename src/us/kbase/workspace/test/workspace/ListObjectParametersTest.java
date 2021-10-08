package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.test.MapBuilder;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.workspace.database.AllUsers;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ListObjectsParameters.ResolvedListObjectParameters;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.RefLimit;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class ListObjectParametersTest {
	
	@Test
	public void buildMinimal() {
		final ListObjectsParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier("foo"))).build();

		assertThat("incorrect workspaces", p.getWorkspaces(),
				is(new HashSet<>(Arrays.asList(new WorkspaceIdentifier("foo")))));
		assertThat("incorrect user", p.getUser(), is(Optional.empty()));
		assertThat("incorrect type", p.getType(), is(Optional.empty()));
		assertThat("incorrect savers", p.getSavers(), is(Collections.emptyList()));
		assertThat("incorrect meta", p.getMetadata(), is(new WorkspaceUserMetadata()));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.buildEmpty()));
		assertThat("incorrect after", p.getAfter(), is(Optional.empty()));
		assertThat("incorrect before", p.getBefore(), is(Optional.empty()));
		assertThat("incorrect minObject", p.getMinObjectID(), is(-1L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(-1L));
		assertThat("incorrect hidden", p.isShowHidden(), is(false));
		assertThat("incorrect deleted", p.isShowDeleted(), is(false));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(false));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(false));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(false));
		assertThat("incorrect as admin", p.asAdmin(), is(false));
		assertThat("incorrect limit", p.getLimit(), is(10000));
	}
	
	@Test
	public void resolveMinimal() {
		final PermissionSet ps = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build();
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier("foo"))).build().resolve(ps);

		assertThat("incorrect permissions", p.getPermissionSet(), is(PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build()));
		assertThat("incorrect type", p.getType(), is(Optional.empty()));
		assertThat("incorrect savers", p.getSavers(), is(Collections.emptyList()));
		assertThat("incorrect meta", p.getMetadata(), is(new WorkspaceUserMetadata()));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.buildEmpty()));
		assertThat("incorrect after", p.getAfter(), is(Optional.empty()));
		assertThat("incorrect before", p.getBefore(), is(Optional.empty()));
		assertThat("incorrect minObject", p.getMinObjectID(), is(-1L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(-1L));
		assertThat("incorrect hidden", p.isShowHidden(), is(false));
		assertThat("incorrect deleted", p.isShowDeleted(), is(false));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(false));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(false));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(false));
		assertThat("incorrect as admin", p.asAdmin(), is(false));
		assertThat("incorrect limit", p.getLimit(), is(10000));
	}
	
	@Test
	public void buildMaximalWithoutStartFrom() throws Exception {
		final List<WorkspaceIdentifier> in = IntStream.range(1, 10001)
				.mapToObj(i -> new WorkspaceIdentifier(i)).collect(Collectors.toList());
		final ListObjectsParameters p = ListObjectsParameters.getBuilder(in)
				.withUser(new WorkspaceUser("bar"))
				.withType(new TypeDefId("Module.Type"))
				.withSavers(Arrays.asList(new WorkspaceUser("hey"), new WorkspaceUser("mom")))
				.withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("whoo", "yay")))
				.withAfter(Instant.ofEpochMilli(20000))
				.withBefore(Instant.ofEpochMilli(30000))
				.withMinObjectID(56)
				.withMaxObjectID(90)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withShowAllVersions(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.withLimit(78)
				.build();

		assertThat("incorrect workspaces", p.getWorkspaces(), is(new HashSet<>(in)));
		assertThat("incorrect user", p.getUser(), is(Optional.of(new WorkspaceUser("bar"))));
		assertThat("incorrect type", p.getType(), is(Optional.of(new TypeDefId("Module.Type"))));
		assertThat("incorrect savers", p.getSavers(), is(
				Arrays.asList(new WorkspaceUser("hey"), new WorkspaceUser("mom"))));
		assertThat("incorrect meta", p.getMetadata(), is(
				new WorkspaceUserMetadata(ImmutableMap.of("whoo", "yay"))));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.buildEmpty()));
		assertThat("incorrect after", p.getAfter(), is(Optional.of(Instant.ofEpochMilli(20000))));
		assertThat("incorrect before", p.getBefore(), is(
				Optional.of(Instant.ofEpochMilli(30000))));
		assertThat("incorrect minObject", p.getMinObjectID(), is(56L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(90L));
		assertThat("incorrect hidden", p.isShowHidden(), is(true));
		assertThat("incorrect deleted", p.isShowDeleted(), is(true));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(true));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(true));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(true));
		assertThat("incorrect as admin", p.asAdmin(), is(true));
		assertThat("incorrect limit", p.getLimit(), is(78));
	}
	
	@Test
	public void buildMaximalWithStartFrom() throws Exception {
		final List<WorkspaceIdentifier> in = IntStream.range(1, 10001)
				.mapToObj(i -> new WorkspaceIdentifier(i)).collect(Collectors.toList());
		final ListObjectsParameters p = ListObjectsParameters.getBuilder(in)
				.withUser(new WorkspaceUser("bar"))
				.withType(new TypeDefId("Module.Type"))
				.withStartFrom(RefLimit.build(3L, 1L, 4))
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withShowAllVersions(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.withLimit(78)
				.build();

		assertThat("incorrect workspaces", p.getWorkspaces(), is(new HashSet<>(in)));
		assertThat("incorrect user", p.getUser(), is(Optional.of(new WorkspaceUser("bar"))));
		assertThat("incorrect type", p.getType(), is(Optional.of(new TypeDefId("Module.Type"))));
		assertThat("incorrect savers", p.getSavers(), is(Collections.emptyList()));
		assertThat("incorrect meta", p.getMetadata(), is(new WorkspaceUserMetadata()));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.build(3L, 1L, 4)));
		assertThat("incorrect after", p.getAfter(), is(Optional.empty()));
		assertThat("incorrect before", p.getBefore(), is(Optional.empty()));
		assertThat("incorrect minObject", p.getMinObjectID(), is(-1L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(-1L));
		assertThat("incorrect hidden", p.isShowHidden(), is(true));
		assertThat("incorrect deleted", p.isShowDeleted(), is(true));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(true));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(true));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(true));
		assertThat("incorrect as admin", p.asAdmin(), is(true));
		assertThat("incorrect limit", p.getLimit(), is(78));
	}
	
	@Test
	public void resolveMaximalWithoutStartFrom() throws Exception {
		final PermissionSet ps = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build();
		final List<WorkspaceIdentifier> in = IntStream.range(1, 10001)
				.mapToObj(i -> new WorkspaceIdentifier(i)).collect(Collectors.toList());
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(in)
				.withUser(new WorkspaceUser("bar"))
				.withType(new TypeDefId("Module.Type"))
				.withSavers(Arrays.asList(new WorkspaceUser("hey"), new WorkspaceUser("mom")))
				.withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("whoo", "yay")))
				.withAfter(Instant.ofEpochMilli(20000))
				.withBefore(Instant.ofEpochMilli(30000))
				.withMinObjectID(56)
				.withMaxObjectID(90)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withShowAllVersions(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.withLimit(78)
				.build()
				.resolve(ps);

		assertThat("incorrect permissions", p.getPermissionSet(), is(PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build()));
		assertThat("incorrect type", p.getType(), is(Optional.of(new TypeDefId("Module.Type"))));
		assertThat("incorrect savers", p.getSavers(), is(
				Arrays.asList(new WorkspaceUser("hey"), new WorkspaceUser("mom"))));
		assertThat("incorrect meta", p.getMetadata(), is(
				new WorkspaceUserMetadata(ImmutableMap.of("whoo", "yay"))));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.buildEmpty()));
		assertThat("incorrect after", p.getAfter(), is(Optional.of(Instant.ofEpochMilli(20000))));
		assertThat("incorrect before", p.getBefore(), is(
				Optional.of(Instant.ofEpochMilli(30000))));
		assertThat("incorrect minObject", p.getMinObjectID(), is(56L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(90L));
		assertThat("incorrect hidden", p.isShowHidden(), is(true));
		assertThat("incorrect deleted", p.isShowDeleted(), is(true));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(true));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(true));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(true));
		assertThat("incorrect as admin", p.asAdmin(), is(true));
		assertThat("incorrect limit", p.getLimit(), is(78));
	}
	
	@Test
	public void resolveMaximalWithStartFrom() throws Exception {
		final PermissionSet ps = PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build();
		final List<WorkspaceIdentifier> in = IntStream.range(1, 10001)
				.mapToObj(i -> new WorkspaceIdentifier(i)).collect(Collectors.toList());
		final ResolvedListObjectParameters p = ListObjectsParameters.getBuilder(in)
				.withUser(new WorkspaceUser("bar"))
				.withType(new TypeDefId("Module.Type"))
				.withStartFrom(RefLimit.build(4L, 2L, null))
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withShowAllVersions(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.withLimit(78)
				.build()
				.resolve(ps);

		assertThat("incorrect permissions", p.getPermissionSet(), is(PermissionSet.getBuilder(
				new WorkspaceUser("foo"), new AllUsers('*')).build()));
		assertThat("incorrect type", p.getType(), is(Optional.of(new TypeDefId("Module.Type"))));
		assertThat("incorrect savers", p.getSavers(), is(Collections.emptyList()));
		assertThat("incorrect meta", p.getMetadata(), is(new WorkspaceUserMetadata()));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.build(4L, 2L, null)));
		assertThat("incorrect after", p.getAfter(), is(Optional.empty()));
		assertThat("incorrect before", p.getBefore(), is(Optional.empty()));
		assertThat("incorrect minObject", p.getMinObjectID(), is(-1L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(-1L));
		assertThat("incorrect hidden", p.isShowHidden(), is(true));
		assertThat("incorrect deleted", p.isShowDeleted(), is(true));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(true));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(true));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(true));
		assertThat("incorrect as admin", p.asAdmin(), is(true));
		assertThat("incorrect limit", p.getLimit(), is(78));
	}
	
	@Test
	public void buildMaximalAndResetAllFieldsToMinimal() throws Exception {
		final ListObjectsParameters p = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier("foo"), new WorkspaceIdentifier(6)))
				.withUser(new WorkspaceUser("bar"))
				.withType(new TypeDefId("Module.Type"))
				.withSavers(Arrays.asList(new WorkspaceUser("hey"), new WorkspaceUser("mom")))
				.withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("whoo", "yay")))
				.withStartFrom(RefLimit.build(42L, null, null))
				.withAfter(Instant.ofEpochMilli(20000))
				.withBefore(Instant.ofEpochMilli(30000))
				.withMinObjectID(56)
				.withMaxObjectID(90)
				.withShowHidden(true)
				.withShowDeleted(true)
				.withShowOnlyDeleted(true)
				.withShowAllVersions(true)
				.withIncludeMetaData(true)
				.withAsAdmin(true)
				.withLimit(78)
				// now reset
				.withUser(null)
				.withType(null)
				.withSavers(null)
				.withMetadata(null)
				.withStartFrom(null)
				.withAfter(null)
				.withBefore(null)
				.withMinObjectID(-40)
				.withMaxObjectID(-89)
				.withShowHidden(false)
				.withShowDeleted(false)
				.withShowOnlyDeleted(false)
				.withShowAllVersions(false)
				.withIncludeMetaData(false)
				.withAsAdmin(false)
				.withLimit(70000)
				.build();
		
		assertThat("incorrect workspaces", p.getWorkspaces(), is(new HashSet<>(Arrays.asList(
				new WorkspaceIdentifier("foo"), new WorkspaceIdentifier(6)))));
		assertThat("incorrect user", p.getUser(), is(Optional.empty()));
		assertThat("incorrect type", p.getType(), is(Optional.empty()));
		assertThat("incorrect savers", p.getSavers(), is(Collections.emptyList()));
		assertThat("incorrect meta", p.getMetadata(), is(new WorkspaceUserMetadata()));
		assertThat("incorrect start", p.getStartFrom(), is(RefLimit.buildEmpty()));
		assertThat("incorrect after", p.getAfter(), is(Optional.empty()));
		assertThat("incorrect before", p.getBefore(), is(Optional.empty()));
		assertThat("incorrect minObject", p.getMinObjectID(), is(-40L));
		assertThat("incorrect maxObject", p.getMaxObjectID(), is(-89L));
		assertThat("incorrect hidden", p.isShowHidden(), is(false));
		assertThat("incorrect deleted", p.isShowDeleted(), is(false));
		assertThat("incorrect only deleted", p.isShowOnlyDeleted(), is(false));
		assertThat("incorrect all vers", p.isShowAllVersions(), is(false));
		assertThat("incorrect include meta", p.isIncludeMetaData(), is(false));
		assertThat("incorrect as admin", p.asAdmin(), is(false));
		assertThat("incorrect limit", p.getLimit(), is(10000));
	}
	
	@Test
	public void buildWithLimits() throws Exception {
		// test various limit values around the cutoffs.
		final Map<Integer, Integer> testCases = MapBuilder.<Integer, Integer>newHashMap()
				.with(-100, 10000)
				.with(-1, 10000)
				.with(0, 10000)
				.with(1, 1)
				.with(2, 2)
				.with(100, 100)
				.with(1000, 1000)
				.with(9999, 9999)
				.with(10000, 10000)
				.with(10001, 10000)
				.with(20000, 10000)
				.with(100000, 10000)
				.build();
		
		for (final Entry<Integer, Integer> testCase: testCases.entrySet()) {
			final ListObjectsParameters p = ListObjectsParameters.getBuilder(
					Arrays.asList(new WorkspaceIdentifier(1)))
					.withLimit(testCase.getKey()).build();
			
			assertThat("incorrect limit for test case " + testCase.getKey(),
					p.getLimit(), is(testCase.getValue()));
		}
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		final String err = "At least one and no more than 10000 workspaces must be specified";
		failGetBuilder(null, new NullPointerException(err));
		failGetBuilder(Collections.emptyList(), new IllegalArgumentException(err));
		
		final List<WorkspaceIdentifier> in = IntStream.range(1, 10002)
				.mapToObj(i -> new WorkspaceIdentifier(i)).collect(Collectors.toList());
		failGetBuilder(in, new IllegalArgumentException(err));
	}
	
	private void failGetBuilder(final List<WorkspaceIdentifier> wsis, final Exception expected) {
		try {
			ListObjectsParameters.getBuilder(wsis);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withMetadataFail() throws Exception {
		try {
			ListObjectsParameters.getBuilder(Arrays.asList(new WorkspaceIdentifier(1)))
					.withMetadata(new WorkspaceUserMetadata(
							ImmutableMap.of("foo", "bar", "baz", "bat")));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new IllegalArgumentException(
					"Only one metadata spec allowed"));
		}
	}
	
	@Test
	public void buildFail() throws Exception {
		final String err = "If a starting reference for paging is " +
				"provided, metadata, savers, min/max object IDs, and timestamps cannot " +
				"be set as filters.";
		final ListObjectsParameters.Builder b = ListObjectsParameters.getBuilder(
				Arrays.asList(new WorkspaceIdentifier(1L)))
				.withStartFrom(RefLimit.build(1L, 1L, 1));
		
		b.withAfter(Instant.ofEpochMilli(10000));
		failBuild(b, new IllegalArgumentException(err));
		
		b.withAfter(null).withBefore(Instant.ofEpochMilli(20000));
		failBuild(b, new IllegalArgumentException(err));
		
		b.withBefore(null).withMetadata(new WorkspaceUserMetadata(ImmutableMap.of("a", "b")));
		failBuild(b, new IllegalArgumentException(err));
		
		b.withMetadata(null).withSavers(Arrays.asList(new WorkspaceUser("a")));
		failBuild(b, new IllegalArgumentException(err));
		
		b.withSavers(null).withMinObjectID(2);
		failBuild(b, new IllegalArgumentException(err));
		
		b.withMinObjectID(1).withMaxObjectID(1);
		failBuild(b, new IllegalArgumentException(err));
	}
	
	private void failBuild(final ListObjectsParameters.Builder b, final Exception expected) {
		try {
			b.build();
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void resolveFail() throws Exception {
		try {
			ListObjectsParameters.getBuilder(Arrays.asList(new WorkspaceIdentifier(1))).build()
					.resolve(null);
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new NullPointerException("perms cannot be null"));
		}
	}
	
	@Test
	public void immutabilty() throws Exception {
		// note WorkspaceMetaData is not immutable
		final List<WorkspaceIdentifier> wsis = new ArrayList<>(
				Arrays.asList(new WorkspaceIdentifier(1)));
		final List<WorkspaceUser> savers = new ArrayList<>(
				Arrays.asList(new WorkspaceUser("foo")));
		final ListObjectsParameters p = ListObjectsParameters.getBuilder(wsis)
				.withSavers(savers)
				.build();
		
		wsis.add(new WorkspaceIdentifier(2));
		assertThat("incorrect workspaces", p.getWorkspaces(), is(new HashSet<>(Arrays.asList(
				new WorkspaceIdentifier(1)))));
		
		savers.add(new WorkspaceUser("bar"));
		assertThat("incorrect savers", p.getSavers(), is(Arrays.asList(new WorkspaceUser("foo"))));
		
		try {
			p.getWorkspaces().add(new WorkspaceIdentifier(2));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new UnsupportedOperationException());
		}
		
		immutableSavers(p);
		// test that the default and null savers list are also immutable
		immutableSavers(ListObjectsParameters.getBuilder(wsis).build());
		immutableSavers(ListObjectsParameters.getBuilder(wsis).withSavers(null).build());
		
		// test the resolved params are immutable, again WSMetadata is mutable
		try {
			ListObjectsParameters.getBuilder(wsis).withSavers(savers).build()
					.resolve(PermissionSet.getBuilder(
							new WorkspaceUser("u"), new AllUsers('*')).build())
					.getSavers().add(new WorkspaceUser("v"));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new UnsupportedOperationException());
		}
	}
	
	private void immutableSavers(final ListObjectsParameters p) {
		try {
			p.getSavers().add(new WorkspaceUser("bar"));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new UnsupportedOperationException());
		}
	}
	
}
