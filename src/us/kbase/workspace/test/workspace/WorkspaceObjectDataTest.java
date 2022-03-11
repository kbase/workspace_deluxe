package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;
import static us.kbase.common.test.TestCommon.opt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;

public class WorkspaceObjectDataTest {
	
	private static final Optional<ByteArrayFileCache> OD = Optional.empty();
	private static final Optional<Reference> OR = Optional.empty();
	private static final SubsetSelection OS = SubsetSelection.EMPTY;
	
	// Provenance really needs a rework and has no hashCode(), so we use identity equality for now
	private static final Provenance PROV = new Provenance(new WorkspaceUser("foo"));
	private static final ObjectInformation INFO = new ObjectInformation(
			1, "foo", "type", new Date(), 1, new WorkspaceUser("u"),
			new ResolvedWorkspaceID(1, "bar", false, false), "chksum", 25, null);
	
	// also has no hashCode(), so identity equality
	// shouldn't have hashCode() anyway, data could be huge
	private static ByteArrayFileCache getBAFC() {
		try {
			return new ByteArrayFileCacheManager()
					.createBAFC(new ByteArrayInputStream("{}".getBytes()), true, true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void buildMinimal() throws Exception {
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV);
		// test the builder from a builder builder
		final WorkspaceObjectData wod2 = WorkspaceObjectData.getBuilder(wodb).build();
		
		buildMinimalAssertions(wodb.build());
		buildMinimalAssertions(wod2);
	}

	public void buildMinimalAssertions(final WorkspaceObjectData wod) {
		assertThat("incorrect info", wod.getObjectInfo(), is(INFO));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(OD));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(OR));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void buildMaximalWithCopyRef() throws Exception {
		final ObjectInformation info2 = INFO.updateReferencePath(Arrays.asList(
				new Reference(3, 4, 5), new Reference(1, 1, 1)));
		final ByteArrayFileCache b = getBAFC();
		
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withData(b)
				.withCopyReference(new Reference(8, 9, 10))
				.withReferences(Arrays.asList("3/4/5", "10/11/12"))
				.withUpdatedReferencePath(Arrays.asList(
						new Reference(3, 4, 5), new Reference(1, 1, 1)))
				.withExternalIDs(new IdReferenceType("t1"), Arrays.asList("foo", "bar"))
				.withExternalIDs(new IdReferenceType("t2"), Arrays.asList("whoo", "whee"))
				.withSubsetSelection(new SubsetSelection(Arrays.asList("/foo")));

		// test the builder from a builder builder
		final WorkspaceObjectData wod2 = WorkspaceObjectData.getBuilder(wodb).build();
		
		buildMaximalWithCopyRefAssertions(info2, b, wodb.build());
		buildMaximalWithCopyRefAssertions(info2, b, wod2);
	}

	public void buildMaximalWithCopyRefAssertions(
			final ObjectInformation info,
			final ByteArrayFileCache b,
			final WorkspaceObjectData wod) {
		assertThat("incorrect info", wod.getObjectInfo(), is(info));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(opt(b)));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(opt(new Reference(8, 9, 10))));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(ImmutableMap.of(
				new IdReferenceType("t1"), Arrays.asList("foo", "bar"),
				new IdReferenceType("t2"), Arrays.asList("whoo", "whee")
				)));
		assertThat("incorrect refs", wod.getReferences(), is(Arrays.asList("3/4/5", "10/11/12")));
		assertThat("incorrect has data", wod.hasData(), is(true));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(),
				is(new SubsetSelection(Arrays.asList("/foo"))));
	}
	
	@Test
	public void buildWithCopyInaccessible() throws Exception {
		// check that it removes the ref
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withCopyReference(new Reference(6, 7, 8))
				.withCopySourceInaccessible();
				
		// test the builder from a builder builder
		final WorkspaceObjectData wod2 = WorkspaceObjectData.getBuilder(wodb).build();
		
		buildWithCopyInaccessibleAssertions(wodb.build());
		buildWithCopyInaccessibleAssertions(wod2);
	}

	public void buildWithCopyInaccessibleAssertions(final WorkspaceObjectData wod) {
		assertThat("incorrect info", wod.getObjectInfo(), is(INFO));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(OD));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(OR));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(true));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void buildAndRemoveInaccessible() throws Exception {
		// check that the inaccessible flag is removed
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withCopySourceInaccessible()
				.withCopyReference(new Reference(6, 7, 8));
		
		// test the builder from a builder builder
		final WorkspaceObjectData wod2 = WorkspaceObjectData.getBuilder(wodb).build();
		
		buildAndRemoveInaccessibleAssertions(wodb.build());
		buildAndRemoveInaccessibleAssertions(wod2);
	}

	public void buildAndRemoveInaccessibleAssertions(final WorkspaceObjectData wod) {
		assertThat("incorrect info", wod.getObjectInfo(), is(INFO));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(OD));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(opt(new Reference(6, 7, 8))));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(Collections.emptyMap()));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void buildAndRemoveWithNulls() throws Exception {
		// add and remove various thingamajigs from the builder other than
		// copyref inaccessible stuff
		final WorkspaceObjectData wod = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withData(getBAFC())
				.withCopyReference(new Reference(8, 9, 10))
				.withReferences(Arrays.asList("3/4/5", "10/11/12"))
				.withExternalIDs(new IdReferenceType("t1"), Arrays.asList("foo", "bar"))
				.withExternalIDs(new IdReferenceType("t2"), Arrays.asList("whoo", "whee"))
				.withSubsetSelection(new SubsetSelection(Arrays.asList("/foo")))
				.withData(null)
				.withCopyReference(null)
				.withReferences(null)
				.withExternalIDs(new IdReferenceType("t1"), null)
				.withSubsetSelection(null)
				.build();
		
		assertThat("incorrect info", wod.getObjectInfo(), is(INFO));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(OD));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(OR));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(ImmutableMap.of(
				new IdReferenceType("t2"), Arrays.asList("whoo", "whee")
				)));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void buildAndRemoveWithEmptyLists() throws Exception {
		// add and remove various thingamajigs from the builder that take lists as input
		final WorkspaceObjectData wod = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withReferences(Arrays.asList("3/4/5", "10/11/12"))
				.withExternalIDs(new IdReferenceType("t1"), Arrays.asList("foo", "bar"))
				.withExternalIDs(new IdReferenceType("t2"), Arrays.asList("whoo", "whee"))
				.withReferences(Collections.emptyList())
				.withExternalIDs(new IdReferenceType("t2"), Collections.emptyList())
				.build();
		
		assertThat("incorrect info", wod.getObjectInfo(), is(INFO));
		assertThat("incorrect prov", wod.getProvenance(), is(PROV));
		assertThat("incorrect data", wod.getSerializedData(), is(OD));
		assertThat("incorrect copy ref", wod.getCopyReference(), is(OR));
		assertThat("incorrect ext ids", wod.getExtractedIds(), is(ImmutableMap.of(
				new IdReferenceType("t1"), Arrays.asList("foo", "bar")
				)));
		assertThat("incorrect refs", wod.getReferences(), is(Collections.emptyList()));
		assertThat("incorrect has data", wod.hasData(), is(false));
		assertThat("incorrect copy inaccessible", wod.isCopySourceInaccessible(), is(false));
		assertThat("incorrect subset", wod.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void builderGetObjectInfo() throws Exception {
		final ObjectInformation info2 = INFO.updateReferencePath(Arrays.asList(
				new Reference(7, 8, 9), new Reference(1, 1, 1)));
		
		final WorkspaceObjectData.Builder b = WorkspaceObjectData.getBuilder(INFO, PROV);
		assertThat("incorrect object info", b.getObjectInfo(), is(INFO));
		
		b.withUpdatedReferencePath(Arrays.asList(new Reference(7, 8, 9), new Reference(1, 1, 1)));
		assertThat("incorrect object info", b.getObjectInfo(), is(info2));
	}
	
	@Test
	public void builderGetCopyReference() throws Exception {
		final WorkspaceObjectData.Builder b = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withCopyReference(new Reference(8, 9, 10));
		
		assertThat("incorrect copy ref", b.getCopyReference(), is(opt(new Reference(8, 9, 10))));
		
		b.withCopyReference(null);
		assertThat("incorrect copy ref", b.getCopyReference(), is(OR));
	}
	
	@Test
	public void builderGetSubsetSelection() throws Exception {
		final WorkspaceObjectData.Builder b = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withSubsetSelection(new SubsetSelection(Arrays.asList("/bar")));
		
		assertThat("incorrect subset", b.getSubsetSelection(),
				is(new SubsetSelection(Arrays.asList("/bar"))));
		
		b.withSubsetSelection(null);
		assertThat("incorrect subset", b.getSubsetSelection(), is(OS));
	}
	
	@Test
	public void destroy() throws Exception {
		final WorkspaceObjectData wod1 = WorkspaceObjectData.getBuilder(INFO, PROV).build();
		// should have no effect since no data
		wod1.destroy();
		assertThat("incorrect data", wod1.getSerializedData(), is(OD));
		
		final ByteArrayFileCache b = getBAFC();
		final WorkspaceObjectData wod2 = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withData(b)
				.build();
		b.containsTrustedJson(); // expect no error
		
		wod2.destroy();
		
		try {
			b.containsTrustedJson();
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new RuntimeException(
					"This ByteArrayFileCache is destroyed"));
		}
	}
	
	@Test
	public void immutableReferences() throws Exception {
		final List<String> refs = new LinkedList<>(Arrays.asList("1/1/1", "2/2/2"));
		
		final WorkspaceObjectData wod = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withReferences(refs)
				.build();
		
		refs.remove(1);
		assertThat("incorrect refs", wod.getReferences(), is(Arrays.asList("1/1/1", "2/2/2")));
		
		try {
			wod.getReferences().remove(1);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes
		}
	}
	
	@Test
	public void immutableExternalIDs() throws Exception {
		final List<String> ids = new LinkedList<>(Arrays.asList("KBH_1", "KBH_3"));
		
		final WorkspaceObjectData wod = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withExternalIDs(new IdReferenceType("handle"), ids)
				.build();
		
		ids.remove(1);
		assertThat("incorrect external IDs", wod.getExtractedIds(), is(ImmutableMap.of(
				new IdReferenceType("handle"), Arrays.asList("KBH_1", "KBH_3"))));
		
		try {
			wod.getExtractedIds().remove(new IdReferenceType("handle"));
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes
		}
		try {
			wod.getExtractedIds().get(new IdReferenceType("handle")).remove(1);
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes
		}
	}
	
	@Test
	public void immutableExternalIDsFromAnotherBuilder() throws Exception {
		final List<String> ids = new LinkedList<>(Arrays.asList("KBH_1", "KBH_3"));
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV)
				.withExternalIDs(new IdReferenceType("handle"), ids);
		
		final WorkspaceObjectData.Builder wodb2 = WorkspaceObjectData.getBuilder(wodb);
		
		final ImmutableMap<IdReferenceType, List<String>> expected = ImmutableMap.of(
				new IdReferenceType("handle"), Arrays.asList("KBH_1", "KBH_3")
				);
		
		assertThat("incorrect ext ids", wodb2.build().getExtractedIds(), is(expected));
		
		// should not affect wodb2
		wodb.withExternalIDs(new IdReferenceType("sample"), Arrays.asList("id1"));
		
		assertThat("incorrect ext ids", wodb2.build().getExtractedIds(), is(expected));
		assertThat("incorrect ext ids", wodb.build().getExtractedIds(), is(ImmutableMap.of(
				new IdReferenceType("handle"), Arrays.asList("KBH_1", "KBH_3"),
				new IdReferenceType("sample"), Arrays.asList("id1")
				)));
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		failGetBuilder(null, PROV, new NullPointerException("info"));
		failGetBuilder(INFO, null, new NullPointerException("prov"));
	}
	
	private void failGetBuilder(
			final ObjectInformation i,
			final Provenance p,
			final Exception expected) {
		try {
			WorkspaceObjectData.getBuilder(i, p);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withReferencesFail() throws Exception {
		failWithReferences(Arrays.asList("1/1/1", null, "2/2/2"), new IllegalArgumentException(
				"Null or whitespace only string in collection references"));
		failWithReferences(Arrays.asList("1/1/1", "2/2/2", "  \t  "), new IllegalArgumentException(
				"Null or whitespace only string in collection references"));
		
	}
	
	private void failWithReferences(final List<String> refs, final Exception expected) {
		try {
			WorkspaceObjectData.getBuilder(INFO, PROV).withReferences(refs);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withExternalIDsFail() throws Exception {
		final IdReferenceType t = new IdReferenceType("t");
		failWithExternalIDs(null, Collections.emptyList(), new NullPointerException("idType"));
		failWithExternalIDs(t, Arrays.asList("id1", null, "id2"), new IllegalArgumentException(
				"Null or whitespace only string in collection ids for type t"));
		failWithExternalIDs(t, Arrays.asList("id1", "id2", "   \t    "),
				new IllegalArgumentException(
						"Null or whitespace only string in collection ids for type t"));
		
	}
	
	private void failWithExternalIDs(
			final IdReferenceType type,
			final List<String> ids,
			final Exception expected) {
		try {
			WorkspaceObjectData.getBuilder(INFO, PROV).withExternalIDs(type, ids);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void withUpdatedReferencePathFail() throws Exception {
		// the actual failure code is in ObjectInformation so we just test a couple modes here
		final WorkspaceObjectData.Builder wodb = WorkspaceObjectData.getBuilder(INFO, PROV);
		failWithUpdatedReferencePath(wodb, null, new IllegalArgumentException(
				"refpath cannot be null or empty"));
		failWithUpdatedReferencePath(wodb, Collections.emptyList(), new IllegalArgumentException(
				"refpath cannot be null or empty"));
		failWithUpdatedReferencePath(wodb, Arrays.asList(null, new Reference(1, 1, 1)),
				new NullPointerException("refpath cannot contain nulls"));
		failWithUpdatedReferencePath(
				wodb, Arrays.asList(new Reference(3, 3, 3), new Reference(1, 2, 1)),
				new IllegalArgumentException(
						"refpath must end with the same reference as the current refpath"));
		
	}
	
	private void failWithUpdatedReferencePath(
			final WorkspaceObjectData.Builder b,
			final List<Reference> refupdate,
			final Exception expected) {
		try {
			b.withUpdatedReferencePath(refupdate);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
