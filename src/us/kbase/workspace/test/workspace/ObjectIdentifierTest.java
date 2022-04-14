package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.workspace.test.LongTextForTestUsage.TEXT255;
import static us.kbase.workspace.test.LongTextForTestUsage.TEXT256;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.common.test.TestCommon.EL;
import static us.kbase.common.test.TestCommon.EI;
import static us.kbase.common.test.TestCommon.opt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.MapBuilder;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class ObjectIdentifierTest {
	
	private static final WorkspaceIdentifier WSI = new WorkspaceIdentifier(100000000);
	
	private static ObjectIdentifier.Builder builder(final WorkspaceIdentifier wsi) {
		return ObjectIdentifier.getBuilder(wsi);
	}
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ObjectIdentifier.class).usingGetClass().verify();
		// handle private inner classes
		for (final Class<?> c: ObjectIdentifier.class.getDeclaredClasses()) {
			if (!c.equals(ObjectIdentifier.Builder.class)) {
				EqualsVerifier.forClass(c).usingGetClass().verify();
			}
		}
	}
	
	private void assertNoNonAddressState(final ObjectIdentifier oi) {
		assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(false));
		assertThat("incorrect refpath", oi.getRefPath(), is(Collections.emptyList()));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	private void assertMinimalState(final ObjectIdentifier oi) {
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(WSI));
		assertThat("incorrect version", oi.getVersion(), is(EI));
		assertNoNonAddressState(oi);
	}
	
	@Test
	public void buildMinimalName() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI).withName("f|o.A-1_2").build();
		
		assertThat("incorrect name", oi.getName(), is(opt("f|o.A-1_2")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
		
		final ObjectIdentifier newoi = ObjectIdentifier.getBuilder(oi).build();
		assertThat("incorrect name", newoi.getName(), is(opt("f|o.A-1_2")));
		assertThat("incorrect id", newoi.getID(), is(EL));
		assertMinimalState(newoi);
	}
	
	@Test
	public void buildMinimalID() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI).withID(1L).build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(1L)));
		assertMinimalState(oi);
		
		final ObjectIdentifier newoi = ObjectIdentifier.getBuilder(oi).build();
		assertThat("incorrect name", newoi.getName(), is(ES));
		assertThat("incorrect id", newoi.getID(), is(opt(1L)));
		assertMinimalState(newoi);
	}
	
	@Test
	public void buildMaximalNameLookupVersionAndSubset() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withName(TEXT255) // test longest name
				.withVersion(1)
				.withLookupRequired(true)
				.withSubsetSelection(new SubsetSelection(Arrays.asList("path1")))
				.build();
		assertOnBuildMaximalNameLookupVersionAndSubset(oi, wsi);
		
		final ObjectIdentifier newoi = ObjectIdentifier.getBuilder(oi).build();
		assertOnBuildMaximalNameLookupVersionAndSubset(newoi, wsi);
	}

	public void assertOnBuildMaximalNameLookupVersionAndSubset(
			final ObjectIdentifier oi,
			final WorkspaceIdentifier wsi) {
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(opt(TEXT255)));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertThat("incorrect version", oi.getVersion(), is(opt(1)));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(true));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(false));
		assertThat("incorrect refpath", oi.getRefPath(), is(Collections.emptyList()));
		assertThat("incorrect subset", oi.getSubSet(),
				is(new SubsetSelection(Arrays.asList("path1"))));
	}
	
	@Test
	public void buildMaximalIDWithRefPath() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("yay");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier(86);
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(wsi1).withID(6L).build();
		final ObjectIdentifier oi2 = ObjectIdentifier.getBuilder(wsi2).withName("thinger").build();
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withID(42L)
				.withVersion(1023)
				.withReferencePath(Arrays.asList(oi1, oi2))
				.build();
		assertOnBuildMaximalIDWithRefPath(oi, wsi, oi1, oi2);
		
		final ObjectIdentifier newoi = ObjectIdentifier.getBuilder(oi).build();
		assertOnBuildMaximalIDWithRefPath(newoi, wsi, oi1, oi2);
	}

	public void assertOnBuildMaximalIDWithRefPath(
			final ObjectIdentifier oi,
			final WorkspaceIdentifier wsi,
			final ObjectIdentifier oi1,
			final ObjectIdentifier oi2) {
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(42L)));
		assertThat("incorrect version", oi.getVersion(), is(opt(1023)));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(true));
		assertThat("incorrect refpath", oi.getRefPath(), is(Arrays.asList(oi1, oi2)));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	@Test
	public void buildNameReplacedWithIDRefpathReplacedWithLookup() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withName("foo")
				.withReferencePath(Arrays.asList(
						ObjectIdentifier.getBuilder(wsi).withID(6L).build()))
				.withID(74L)
				.withLookupRequired(true)
				.build();
		
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(74L)));
		assertThat("incorrect version", oi.getVersion(), is(EI));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(true));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(false));
		assertThat("incorrect refpath", oi.getRefPath(), is(Collections.emptyList()));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	@Test
	public void buildIDReplacedWithNameLookupReplacedWithRefpath() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(wsi).withID(6L).build();
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withID(74L)
				.withLookupRequired(true)
				.withName("foo")
				.withReferencePath(Arrays.asList(oi1))
				.build();
		
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertThat("incorrect version", oi.getVersion(), is(EI));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(true));
		assertThat("incorrect refpath", oi.getRefPath(), is(Arrays.asList(oi1)));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	@Test
	public void buildRemoveWithNullReferencePath() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(74L)
				.withVersion(89)
				.withReferencePath(Arrays.asList(
						ObjectIdentifier.getBuilder(WSI).withID(6L).build()))
				.withSubsetSelection(new SubsetSelection(Arrays.asList("path")))
				.withReferencePath(null)
				.withSubsetSelection(null)
				.withVersion(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(74L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildRemoveEmptyReferencePath() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(74L)
				.withReferencePath(Arrays.asList(
						ObjectIdentifier.getBuilder(WSI).withID(6L).build()))
				.withReferencePath(Collections.emptyList())
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(74L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildNullEmptyReferencePathDoesNotAffectLookup() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		for (final List<ObjectIdentifier> loi: Arrays.<List<ObjectIdentifier>>asList(
				null, Collections.emptyList())) {
			final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
					.withID(74L)
					.withLookupRequired(true)
					.withReferencePath(loi)
					.build();
			
			assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
			assertThat("incorrect name", oi.getName(), is(ES));
			assertThat("incorrect id", oi.getID(), is(opt(74L)));
			assertThat("incorrect version", oi.getVersion(), is(ES));
			assertThat("incorrect lookup", oi.isLookupRequired(), is(true));
			assertThat("incorrect hasrefpath", oi.hasRefPath(), is(false));
			assertThat("incorrect refpath", oi.getRefPath(), is(Collections.emptyList()));
			assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
		}
	}
	
	@Test
	public void buildRemoveLookup() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(74L)
				.withLookupRequired(true)
				.withLookupRequired(false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(74L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildFalseLookupDoesNotAffectReferencePath() {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(wsi).withID(6L).build();
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withID(74L)
				.withReferencePath(Arrays.asList(oi1))
				.withLookupRequired(false)
				.build();
		
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(74L)));
		assertThat("incorrect version", oi.getVersion(), is(ES));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(true));
		assertThat("incorrect refpath", oi.getRefPath(), is(Arrays.asList(oi1)));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	@Test
	public void buildNameNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo")
				.withName(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(84L)
				.withName(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(84L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(98L)
				.withID(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(98L)));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo")
				.withID(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildNameWithBoolean() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo", true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(2L) // test exception isn't thrown
				.withName("foo", false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
	}
		
	@Test
	public void buildNameWithBooleanNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withName(null, true) // should not throw exception
				.withName("foo", true)
				.withName(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(78L)
				.withName(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(78L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDWithBoolean() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(2L, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(2L)));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo") // test exception isn't thrown
				.withID(6L, false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(6L)));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDWithBooleanNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(null, true) // should not throw exception
				.withID(3000L, true)
				.withID(null, false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(ES));
		assertThat("incorrect id", oi.getID(), is(opt(3000L)));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo")
				.withID(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(opt("foo")));
		assertThat("incorrect id", oi.getID(), is(EL));
		assertMinimalState(oi);
	}
	
	@Test
	public void refPathImmutable() throws Exception {
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(WSI).withID(8L).build();
		final ObjectIdentifier oi2 = ObjectIdentifier.getBuilder(WSI).withID(10L).build();
		final List<ObjectIdentifier> loi = new ArrayList<>(Arrays.asList(oi1));
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(7L)
				.withReferencePath(loi)
				.build();
		
		// test modifying the input path
		loi.add(oi2);
		assertThat("incorrect refpath", oi.getRefPath(), is(Arrays.asList(oi1)));
		
		// test modifying the returned path
		failModifyRefPath(oi, oi2);
		
		// test from ref path string
		final ObjectIdentifier roi = ObjectIdentifier.getBuilderFromRefPath("1/1/1;2/2/2").build();
		failModifyRefPath(roi, oi2);
		
	}

	public void failModifyRefPath(final ObjectIdentifier target, final ObjectIdentifier toAdd) {
		try {
			target.getRefPath().add(toAdd);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	private final static class RefTestCase {
		final String ref;
		final WorkspaceIdentifier wsi;
		final Optional<String> name;
		final Optional<Long> id;
		final Optional<Integer> version;
		final List<ObjectIdentifier> refpath;

		public RefTestCase(
				final String ref,
				final WorkspaceIdentifier wsi,
				final Optional<String> name,
				final Optional<Long> id,
				final Optional<Integer> version) {
			this.ref = ref;
			this.wsi = wsi;
			this.name = name;
			this.id = id;
			this.version = version;
			this.refpath = null;
		}
		
		public RefTestCase(
				final String ref,
				final WorkspaceIdentifier wsi,
				final Optional<String> name,
				final Optional<Long> id,
				final Optional<Integer> version,
				final List<ObjectIdentifier> refpath) {
			this.ref = ref;
			this.wsi = wsi;
			this.name = name;
			this.id = id;
			this.version = version;
			this.refpath = refpath;
		}
	}
	
	@Test
	public void buildFromRefAndValidate() throws Exception {
		final WorkspaceIdentifier wfoo = new WorkspaceIdentifier("foo");
		final WorkspaceIdentifier wufoo = new WorkspaceIdentifier("user1:foo");
		final WorkspaceIdentifier wfoA = new WorkspaceIdentifier("fo.A-1_2");
		final WorkspaceIdentifier wwhoo = new WorkspaceIdentifier("whoo");
		final WorkspaceIdentifier w1 = new WorkspaceIdentifier(1);
		final WorkspaceIdentifier w2 = new WorkspaceIdentifier(2);
		final WorkspaceIdentifier w89 = new WorkspaceIdentifier(89);

		// we use WorkspaceIdentifier under the hood to store the workspace ID and so don't 
		// exhaustively test valid workspace IDs / Names here
		final List<RefTestCase> tests = list(
				new RefTestCase("fo.A-1_2/f|o.A-1_2/1", wfoA, opt("f|o.A-1_2"), EL, opt(1)),
				new RefTestCase("1/" + TEXT255, w1, opt(TEXT255), EL, EI),
				new RefTestCase("1/1/1", w1, ES, opt(1L), opt(1)),
				new RefTestCase("user1:foo/bar", wufoo, opt("bar"), EL, EI),
				new RefTestCase("foo/bar/1", wfoo, opt("bar"), EL, opt(1)),
				new RefTestCase("2/49", w2, ES, opt(49L), EI),
				new RefTestCase("1/1/60", w1, ES, opt(1L), opt(60)),
				new RefTestCase("whoo/23/91", wwhoo, ES, opt(23L), opt(91)),
				new RefTestCase("89/what", w89, opt("what"), EL, EI),
				new RefTestCase("   \t  89/what/32  \t  ", w89, opt("what"), EL, opt(32)),
				new RefTestCase("whoo/6", wwhoo, ES, opt(6L), EI),
				new RefTestCase("whoo/89/", wwhoo, ES, opt(89L), EI)  // trailing slash ok
				);
		
		for (final RefTestCase t: tests) {
			final ObjectIdentifier oi = ObjectIdentifier.getBuilder(t.ref).build();
			
			checkRefTestCase(oi, t);
			
			ObjectIdentifier.validateReference(t.ref, false); // no exception == passed
			
			// test that the ref path builder processes refs identically
			final ObjectIdentifier roi = ObjectIdentifier.getBuilderFromRefPath(t.ref).build();
			
			checkRefTestCase(roi, t);
			
			ObjectIdentifier.validateReferencePath(t.ref, false); // no exception == passed
		}
	}

	public void checkRefTestCase(final ObjectIdentifier oi, final RefTestCase t) {
		assertThat("incorrect wsi for ref " + t.ref, oi.getWorkspaceIdentifier(), is(t.wsi));
		assertThat("incorrect name for ref " + t.ref, oi.getName(), is(t.name));
		assertThat("incorrect id for ref " + t.ref, oi.getID(), is(t.id));
		assertThat("incorrect version for ref" + t.ref, oi.getVersion(), is(t.version));
		assertNoNonAddressState(oi);
	}
	
	@Test
	public void buildFromRefPathAndValidate() throws Exception {
		final WorkspaceIdentifier wfoo = new WorkspaceIdentifier("foo");
		final WorkspaceIdentifier wufoo = new WorkspaceIdentifier("user1:foo");
		final WorkspaceIdentifier wfoA = new WorkspaceIdentifier("fo.A-1_2");
		final WorkspaceIdentifier wwhoo = new WorkspaceIdentifier("whoo");
		final WorkspaceIdentifier w1 = new WorkspaceIdentifier(1);
		final WorkspaceIdentifier w2 = new WorkspaceIdentifier(2);
		final WorkspaceIdentifier w89 = new WorkspaceIdentifier(89);
		final List<ObjectIdentifier> oi1 = list(
				builder(w1).withID(2L).withVersion(3).build(),
				builder(wfoo).withName("bar").build());
		final List<ObjectIdentifier> oi2 = list(
				builder(wfoA).withName("f|o.A-1_2").withVersion(1).build());
		final List<ObjectIdentifier> oi3 = list(
				builder(wwhoo).withID(89L).build(),
				builder(w2).withID(3L).build(),
				builder(wufoo).withName("baz").withVersion(1).build(),
				builder(w1).withName(TEXT255).build());
		final List<ObjectIdentifier> oi4 = list(builder(w1).withID(1L).withVersion(1).build());
		final List<ObjectIdentifier> oi5 = list(builder(wufoo).withName("bat").build());
		final List<ObjectIdentifier> oi6 = list(
				builder(wwhoo).withName("whee").withVersion(3).build());
		final List<ObjectIdentifier> oi7 = list(builder(w2).withID(101L).build());
		final List<ObjectIdentifier> oi8 = list(builder(w1).withID(1L).withVersion(34).build());
		final List<ObjectIdentifier> oi9 = list(
				builder(wwhoo).withID(65L).withVersion(78).build());
		final List<ObjectIdentifier> oi10 = list(builder(w89).withName("what").build());
		final List<ObjectIdentifier> oi11 = list(
				builder(w89).withName("what").withVersion(32).build());
		final List<ObjectIdentifier> oi12 = list(builder(wwhoo).withID(6L).build());

		// we use WorkspaceIdentifier under the hood to check the workspace ID and so don't 
		// exhaustively test valid workspace IDs / Names here
		final List<RefTestCase> tests = list(
				// test trailing whitespace after ; separator ok
				new RefTestCase("   fo.A-1_2/f|o.A-1_2/1   \t  ;  \t   1/2/3 ; foo/bar   ;    ",
						wfoA, opt("f|o.A-1_2"), EL, opt(1), oi1),
				new RefTestCase("1/" + TEXT255 + "  ;  fo.A-1_2/f|o.A-1_2/1",
						w1, opt(TEXT255), EL, EI, oi2),
				// check trailing slash ok
				new RefTestCase("1/1/1;whoo/89/;2/3/  \t  ;user1:foo/baz/1;1/" + TEXT255,
						w1, ES, opt(1L), opt(1), oi3),
				new RefTestCase("user1:foo/bar  ;   1/1/1 \t \n", wufoo, opt("bar"), EL, EI, oi4),
				new RefTestCase("foo/bar/1;user1:foo/bat", wfoo, opt("bar"), EL, opt(1), oi5),
				new RefTestCase("2/49; whoo/whee/3", w2, ES, opt(49L), EI, oi6),
				new RefTestCase("1/1/60;  2/101", w1, ES, opt(1L), opt(60), oi7),
				new RefTestCase("whoo/23/91;1/1/34", wwhoo, ES, opt(23L), opt(91), oi8),
				new RefTestCase("89/what;whoo/65/78", w89, opt("what"), EL, EI, oi9),
				new RefTestCase("   \t  89/what/32  \t  ; 89/what",
						w89, opt("what"), EL, opt(32), oi10),
				new RefTestCase("whoo/6; 89/what/32", wwhoo, ES, opt(6L), EI, oi11),
				new RefTestCase("whoo/89/;whoo/6", wwhoo, ES, opt(89L), EI, oi12)
				);
		
		for (final RefTestCase t: tests) {
			final ObjectIdentifier oi = ObjectIdentifier.getBuilderFromRefPath(t.ref).build();
			
			assertThat("incorrect wsi for ref " + t.ref, oi.getWorkspaceIdentifier(), is(t.wsi));
			assertThat("incorrect name for ref " + t.ref, oi.getName(), is(t.name));
			assertThat("incorrect id for ref " + t.ref, oi.getID(), is(t.id));
			assertThat("incorrect version for ref " + t.ref, oi.getVersion(), is(t.version));
			assertThat("incorrect refpath for ref " + t.ref, oi.getRefPath(), is(t.refpath));
			assertThat("incorrect hasrefpath", oi.hasRefPath(), is(true));
			assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
			assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
			
			ObjectIdentifier.validateReferencePath(t.ref, false); // no exception == passed
		}
	}
	
	@Test
	public void getBuilderFailNull() throws Exception {
		try {
			ObjectIdentifier.getBuilder((WorkspaceIdentifier) null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("wsi"));
		}
		try {
			ObjectIdentifier.getBuilder((ObjectIdentifier) null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("oi"));
		}
		try {
			ObjectIdentifier.getBuilder((ObjectIdentifier.Builder) null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("b"));
		}
	}
	
	@Test
	public void getBuilderRefAndValidateFail() throws Exception {
		// we use WorkspaceIdentifier under the hood to store the workspace ID and so don't 
		// exhaustively test invalid workspace IDs / Names here
		failGetBuilderAndValidateRef(null, new IllegalArgumentException(
				"reference cannot be null or the empty string"));
		failGetBuilderAndValidateRef("1/1/1;2/2/2", new IllegalArgumentException(
				"Illegal number of separators '/' in object reference '1/1/1;2/2/2'"));
		final Map<String, Exception> testCases = MapBuilder.<String, Exception>newHashMap()
				.with("  \t   ", new IllegalArgumentException(
						"Illegal number of separators '/' in object reference '  \t   '"))
				.with("1", new IllegalArgumentException(
						"Illegal number of separators '/' in object reference '1'"))
				.with("foo", new IllegalArgumentException(
						"Illegal number of separators '/' in object reference 'foo'"))
				.with("foo/1/3/4", new IllegalArgumentException(
						"Illegal number of separators '/' in object reference 'foo/1/3/4'"))
				.with("user1|foo/1/3", new IllegalArgumentException(
						"Illegal character in workspace name user1|foo: |"))
				.with("foo/b*ar/3", new IllegalArgumentException(
						"Illegal character in object name b*ar: *"))
				.with("foo/" + TEXT256, new IllegalArgumentException(
						"Object name exceeds the maximum length of 255"))
				.with("0/bar/3", new IllegalArgumentException("Workspace id must be > 0"))
				.with("-10000/bar/3", new IllegalArgumentException("Workspace id must be > 0"))
				.with("/1/3", new IllegalArgumentException(
						"Workspace name cannot be null or the empty string"))
				.with("1//3", new IllegalArgumentException(
						"Object name cannot be null or the empty string"))
				.with("f/0/3", new IllegalArgumentException("Object id must be > 0"))
				.with("f/-1/3", new IllegalArgumentException("Object id must be > 0"))
				.with("f/1/0", new IllegalArgumentException("Object version must be > 0"))
				.with("f/1/-10", new IllegalArgumentException("Object version must be > 0"))
				.with("f/1/n", new IllegalArgumentException(
						"Unable to parse version portion of object reference 'f/1/n' to an integer"
						))
				.build();
		
		for (final String ref: testCases.keySet()) {
			failGetBuilderAndValidateRef(ref, testCases.get(ref));
			failGetBuilderFromRefPathAndValidate(ref, testCases.get(ref));
		}
	}
	
	private static final String f(final String formatString, final Object... args) {
		return String.format(formatString, args);
	}
	
	@Test
	public void getBuilderFromRefPathAndValidateFail() throws Exception {
		failGetBuilderFromRefPathAndValidate(null, new IllegalArgumentException(
				"refpath cannot be null or the empty string"));
		final String one = "1/1/1;";
		final Map<String, Exception> testCases = MapBuilder.<String, Exception>newHashMap()
				.with(f("%s  \t   ;   ", one), new IllegalArgumentException(
						"Reference path position 2: "
						+ "Illegal number of separators '/' in object reference '  \t   '"))
				.with(f("%s%s1", one, one), new IllegalArgumentException(
						"Reference path position 3: "
						+ "Illegal number of separators '/' in object reference '1'"))
				.with(f("foo;%s", one), new IllegalArgumentException(
						"Reference path position 1: "
						+ "Illegal number of separators '/' in object reference 'foo'"))
				.with(f("%s%s%s%sfoo/1/3/4", one, one, one, one), new IllegalArgumentException(
						"Reference path position 5: "
						+ "Illegal number of separators '/' in object reference 'foo/1/3/4'"))
				.with(f("%suser1|foo/1/3", one), new IllegalArgumentException(
						"Reference path position 2: "
						+ "Illegal character in workspace name user1|foo: |"))
				.with(f("%s%sfoo/b*ar/3", one, one), new IllegalArgumentException(
						"Reference path position 3: "
						+ "Illegal character in object name b*ar: *"))
				.with(f("foo/%s;%s", TEXT256, one), new IllegalArgumentException(
						"Reference path position 1: "
						+ "Object name exceeds the maximum length of 255"))
				.with(f("%s0/bar/3", one), new IllegalArgumentException(
						"Reference path position 2: Workspace id must be > 0"))
				.with(f("%s%s-10000/bar/3", one, one), new IllegalArgumentException(
						"Reference path position 3: Workspace id must be > 0"))
				.with(f("%s/1/3", one), new IllegalArgumentException(
						"Reference path position 2: "
						+ "Workspace name cannot be null or the empty string"))
				.with(f("%s%s%s1//3", one, one, one), new IllegalArgumentException(
						"Reference path position 4: "
						+ "Object name cannot be null or the empty string"))
				.with(f("%sf/0/3", one), new IllegalArgumentException(
						"Reference path position 2: Object id must be > 0"))
				.with(f("f/-1/3;%s", one), new IllegalArgumentException(
						"Reference path position 1: Object id must be > 0"))
				.with(f("%s%s%s%s%sf/1/0", one, one, one, one, one), new IllegalArgumentException(
						"Reference path position 6: Object version must be > 0"))
				.with(f("%sf/1/-10", one), new IllegalArgumentException(
						"Reference path position 2: Object version must be > 0"))
				.with(f("%s%sf/1/n", one, one), new IllegalArgumentException(
						"Reference path position 3: "
						+ "Unable to parse version portion of object reference 'f/1/n' to an "
						+ "integer"
						))
				.build();
		
		for (final String ref: testCases.keySet()) {
			failGetBuilderFromRefPathAndValidate(ref, testCases.get(ref));
		}
	}

	private void failGetBuilderAndValidateRef(final String ref, final Exception expected) {
		try {
			ObjectIdentifier.getBuilder(ref);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		
		try {
			ObjectIdentifier.validateReference(ref, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private void failGetBuilderFromRefPathAndValidate(final String ref, final Exception expected) {
		try {
			ObjectIdentifier.getBuilderFromRefPath(ref);
			fail("expected exception for ref " + ref);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		try {
			ObjectIdentifier.validateReferencePath(ref, false);
			fail("expected exception for ref " + ref);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withNameFail() throws Exception {
		final List<List<String>> testSet = list(
				list("", "Object name cannot be null or the empty string"),
				list("f|o.A-1_2+", "Illegal character in object name f|o.A-1_2+: +"),
				list("hey now", "Illegal character in object name hey now:  "),
				list("-1", "Object names cannot be integers: -1"),
				list("2345678901", "Object names cannot be integers: 2345678901"),
				list("23456789012345678901",
						"Object names cannot be integers: 23456789012345678901"),
				list("15", "Object names cannot be integers: 15"),
				list(TEXT256, "Object name exceeds the maximum length of 255")
				);
		
		for (final List<String> testArgs: testSet) {
			failWithName(testArgs.get(0), testArgs.get(1));
		}
	}
	
	private void failWithName(final String name, final String msg) {
		try {
			ObjectIdentifier.getBuilder(WSI).withName(name);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(msg));
		}
		try {
			ObjectIdentifier.getBuilder(WSI).withName(name, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(msg));
		}
	}
	
	@Test
	public void withNameBooleanFail() throws Exception {
		try {
			ObjectIdentifier.getBuilder(WSI).withID(2L).withName("foo", true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Must provide one and only one of object name (was: foo) or id (was: 2)"));
		}
	}
	
	@Test
	public void withIDFail() throws Exception {
		final List<List<Object>> testSet = list(
				list(0L, "Object id must be > 0"),
				list(-382L, "Object id must be > 0")
				);
		
		for (final List<Object> testArgs: testSet) {
			failWithID((long) testArgs.get(0), (String) testArgs.get(1));
		}
	}
	
	private void failWithID(final long id, final String msg) {
		try {
			ObjectIdentifier.getBuilder(WSI).withID(id);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(msg));
		}
		try {
			ObjectIdentifier.getBuilder(WSI).withID(id, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(msg));
		}
	}
	
	@Test
	public void withIDBooleanFail() throws Exception {
		try {
			ObjectIdentifier.getBuilder(WSI).withName("whee").withID(64L, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Must provide one and only one of object name (was: whee) or id (was: 64)"));
		}
	}
	
	@Test
	public void withVersionFail() throws Exception {
		for (final int ver: Arrays.asList(0, -100)) {
			try {
				ObjectIdentifier.getBuilder(WSI).withVersion(ver);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Object version must be > 0"));
			}
		}
	}
	
	@Test
	public void withRefPathFail() throws Exception {
		try {
			ObjectIdentifier.getBuilder(WSI).withReferencePath(Arrays.asList(
					ObjectIdentifier.getBuilder(WSI).withID(1L).build(),
					null,
					ObjectIdentifier.getBuilder(WSI).withID(2L).build()
					));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException(
					"Nulls are not allowed in reference paths"));
		}
	}
	
	@Test
	public void buildFail() throws Exception {
		final ObjectIdentifier.Builder b = ObjectIdentifier.getBuilder(WSI);
		failBuild(b);
		failBuild(b.withID(null).withName(null));
	}
	
	private void failBuild(final ObjectIdentifier.Builder build) {
		try {
			build.build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Must provide one and only one of object name (was: null) or id (was: null)"));
		}
	}
	
	@Test
	public void validateReferenceAbsolute() throws Exception {
		// this only tests absolute references. The other conditions are tested in
		// the getBuilderAndValidate tests.
		for (final String ref: list("1/1/1", "100/1000/10000")) {
			ObjectIdentifier.validateReference(ref, true);
			ObjectIdentifier.validateReferencePath(ref, true);
		}
	}
	
	@Test
	public void validateReferencePathAbsolute() throws Exception {
		// this only tests absolute references. The other conditions are tested in
		// the getBuilderFromRefPathAndValidate tests.
		for (final String ref: list("1/1/1;6/6/6", "100/1000/10000;1/1/1;5/6/7")) {
			ObjectIdentifier.validateReferencePath(ref, true);
		}
	}
	
	@Test
	public void validateReferenceAbsoluteFail() throws Exception {
		// this only tests failing absolute references. The other error conditions are tested in
		// the getBuilderAndValidate tests.
		for (final String ref: list("1/1", "foo/1", "1/foo", "foo/1/1", "1/foo/1")) {
			failValidateReferenceAbsolute(ref);
		}
	}
	
	private class RefPathAbsoluteTestCase {
		private String refpath;
		private String ref;
		private int position;

		public RefPathAbsoluteTestCase(String refpath, String ref, int position) {
			this.refpath = refpath;
			this.ref = ref;
			this.position = position;
		}
	}
	
	@Test
	public void validateReferencePathAbsoluteFail() throws Exception {
		// this only tests failing absolute reference paths. The other error conditions are
		// tested in the getBuilderFromRefpathAndValidate tests.
		final List<RefPathAbsoluteTestCase> testCases = list(
				new RefPathAbsoluteTestCase("1/1/1;1/1", "1/1", 2),
				new RefPathAbsoluteTestCase("foo/1;1/1/1", "foo/1", 1),
				new RefPathAbsoluteTestCase("1/1/1;1/1/1;1/foo;1/1/1", "1/foo", 3),
				new RefPathAbsoluteTestCase("1/1/1;1/1/1;1/1/1;foo/1/1", "foo/1/1", 4),
				new RefPathAbsoluteTestCase("1/1/1;1/1/1;1/1/1;1/1/1;1/foo/1", "1/foo/1", 5)
				);
		for (final RefPathAbsoluteTestCase t: testCases) {
			failValidateReferencePathAbsolute(t.refpath, f(
					"Reference path position %s: Reference %s is not absolute",
					t.position, t.ref));
		}
	}
	private void failValidateReferenceAbsolute(final String ref) {
		ObjectIdentifier.validateReference(ref, false); // should pass
		final String err = f("Reference %s is not absolute", ref);
		try {
			ObjectIdentifier.validateReference(ref, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(err));
		}
		// test that the ref path processor behaves the same way
		failValidateReferencePathAbsolute(ref, err);
	}
	
	private void failValidateReferencePathAbsolute(final String ref, final String expected) {
		ObjectIdentifier.validateReferencePath(ref, false); // should pass
		try {
			ObjectIdentifier.validateReferencePath(ref, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(expected));
		}
	}
	
	@Test
	public void isAbsolute() throws Exception {
		assertThat("incorrect absolute",
				ObjectIdentifier.getBuilder("1/1/1").build().isAbsolute(), is(true));
		
		for (final String falseCase: list("1/1", "foo/1", "1/foo", "foo/1/1", "1/foo/1")) {
			assertThat("incorrect absolute for ref " + falseCase,
					ObjectIdentifier.getBuilder(falseCase).build().isAbsolute(), is(false));
		}
	}
	
	@Test
	public void getLast() throws Exception {
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("yay");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier(86);
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(wsi1).withID(6L).build();
		final ObjectIdentifier oi2 = ObjectIdentifier.getBuilder(wsi2).withName("thinger").build();
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(wsi)
				.withID(42L)
				.withVersion(1023)
				.withReferencePath(Arrays.asList(oi1, oi2))
				.build();
		
		assertThat("incorrect getLast", oi.getLast(), is(oi2));
	}
	
	@Test
	public void getLastFail() throws Exception {
		final String err = "This object identifier has no reference path";
		
		failGetLast(ObjectIdentifier.getBuilder(WSI).withID(1L).build(), err);
		
		final ObjectIdentifier oi2 = ObjectIdentifier.getBuilder(WSI)
				.withID(1L)
				.withLookupRequired(true)
				.build();
		failGetLast(oi2, err);
		
		final ObjectIdentifier oi3 = ObjectIdentifier.getBuilder(WSI)
				.withID(1L)
				.withSubsetSelection(new SubsetSelection(Arrays.asList("p")))
				.build();
		failGetLast(oi3, err);
	}
	
	private void failGetLast(final ObjectIdentifier oi, final String msg) {
		try {
			oi.getLast();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalStateException(msg));
		}
	}
	
	@Test
	public void copyBuilderWithNameVersionSubsetAndRefpath() throws Exception {
		// copy one of the 2 exclusionary states of the builder.
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final WorkspaceIdentifier wsi1 = new WorkspaceIdentifier("yay");
		final WorkspaceIdentifier wsi2 = new WorkspaceIdentifier(86);
		final ObjectIdentifier oi1 = ObjectIdentifier.getBuilder(wsi1).withID(6L).build();
		final ObjectIdentifier oi2 = ObjectIdentifier.getBuilder(wsi2).withName("thinger").build();
		final ObjectIdentifier.Builder b = ObjectIdentifier.getBuilder(wsi)
				.withName("stuff")
				.withReferencePath(Arrays.asList(oi1, oi2))
				.withSubsetSelection(new SubsetSelection(Arrays.asList("path1")))
				.withVersion(24);
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(b).build();
		
		assertThat("incorrect build", oi, is(b.build()));
	}
	
	@Test
	public void copyBuilderWithIDAndLookup() throws Exception {
		// copy the other of the 2 exclusionary states of the builder.
		final WorkspaceIdentifier wsi = new WorkspaceIdentifier(72);
		final ObjectIdentifier.Builder b = ObjectIdentifier.getBuilder(wsi)
				.withID(42L)
				.withLookupRequired(true);
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(b).build();
		
		assertThat("incorrect build", oi, is(b.build()));
	}
}
