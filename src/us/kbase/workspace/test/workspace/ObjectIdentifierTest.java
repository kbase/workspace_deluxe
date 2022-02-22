package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.workspace.test.LongTextForTestUsage.TEXT255;
import static us.kbase.workspace.test.LongTextForTestUsage.TEXT256;
import static us.kbase.common.test.TestCommon.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class ObjectIdentifierTest {
	
	private static final WorkspaceIdentifier WSI = new WorkspaceIdentifier(100000000);
	
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
	
	private void assertMinimalState(final ObjectIdentifier oi) {
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(WSI));
		assertThat("incorrect version", oi.getVersion(), is(nullValue()));
		assertThat("incorrect lookup", oi.isLookupRequired(), is(false));
		assertThat("incorrect hasrefpath", oi.hasRefPath(), is(false));
		assertThat("incorrect refpath", oi.getRefPath(), is(Collections.emptyList()));
		assertThat("incorrect subset", oi.getSubSet(), is(SubsetSelection.EMPTY));
	}
	
	@Test
	public void buildMinimalName() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI).withName("f|o.A-1_2").build();
		
		assertThat("incorrect name", oi.getName(), is("f|o.A-1_2"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildMinimalID() {
		final ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI).withID(1L).build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(1L));
		assertMinimalState(oi);
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
		
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(TEXT255));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertThat("incorrect version", oi.getVersion(), is(1));
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
		
		assertThat("incorrect wsi", oi.getWorkspaceIdentifier(), is(wsi));
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(42L));
		assertThat("incorrect version", oi.getVersion(), is(1023));
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
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(74L));
		assertThat("incorrect version", oi.getVersion(), is(nullValue()));
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
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertThat("incorrect version", oi.getVersion(), is(nullValue()));
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
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(74L));
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
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(74L));
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
			assertThat("incorrect name", oi.getName(), is(nullValue()));
			assertThat("incorrect id", oi.getId(), is(74L));
			assertThat("incorrect version", oi.getVersion(), is(nullValue()));
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
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(74L));
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
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(74L));
		assertThat("incorrect version", oi.getVersion(), is(nullValue()));
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
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(84L)
				.withName(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(84L));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(98L)
				.withID(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(98L));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo")
				.withID(null)
				.build();
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildNameWithBoolean() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo", true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(2L) // test exception isn't thrown
				.withName("foo", false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
	}
		
	@Test
	public void buildNameWithBooleanNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withName(null, true) // should not throw exception
				.withName("foo", true)
				.withName(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withID(78L)
				.withName(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(78L));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDWithBoolean() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(2L, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(2L));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo") // test exception isn't thrown
				.withID(6L, false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(6L));
		assertMinimalState(oi);
	}
	
	@Test
	public void buildIDWithBooleanNoop() throws Exception {
		ObjectIdentifier oi = ObjectIdentifier.getBuilder(WSI)
				.withID(null, true) // should not throw exception
				.withID(3000L, true)
				.withID(null, false)
				.build();
		
		assertThat("incorrect name", oi.getName(), is(nullValue()));
		assertThat("incorrect id", oi.getId(), is(3000L));
		assertMinimalState(oi);
		
		oi = ObjectIdentifier.getBuilder(WSI)
				.withName("foo")
				.withID(null, true)
				.build();
		
		assertThat("incorrect name", oi.getName(), is("foo"));
		assertThat("incorrect id", oi.getId(), is(nullValue()));
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
		try {
			oi.getRefPath().add(oi2);
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		try {
			ObjectIdentifier.getBuilder(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("wsi"));
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
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
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
}
