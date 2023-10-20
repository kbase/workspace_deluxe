package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUser;

public class ObjectInformationTest {
	
	private static final AbsoluteTypeDefId TYPE = new AbsoluteTypeDefId(
			new TypeDefName("type.t"), 3, 7);
	private static final Instant INST = inst(10000);
	private static final MD5 MDFIVE = new MD5("9a5b862b3f6969ec491ddeea83590e04");
	private static final Map<String, String> MT = Collections.emptyMap();
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ObjectInformation.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimalWithStandardInputs() {
		final ObjectInformation oi = minimalOI();
		assertThat("incorrect obj id", oi.getObjectId(), is(1L));
		assertThat("incorrect obj name", oi.getObjectName(), is("foo"));
		assertThat("incorrect obj type", oi.getTypeString(), is("type.t-3.7"));
		assertThat("incorrect obj date", oi.getSavedDate(), is(inst(10000)));
		assertThat("incorrect obj ver", oi.getVersion(), is(3));
		assertThat("incorrect obj user", oi.getSavedBy(), is(new WorkspaceUser("bar")));
		assertThat("incorrect obj ws name", oi.getWorkspaceName(), is("whee"));
		assertThat("incorrect obj ws id", oi.getWorkspaceId(), is(4L));
		assertThat("incorrect obj checksum", oi.getCheckSum(),
				is("9a5b862b3f6969ec491ddeea83590e04"));
		assertThat("incorrect obj size", oi.getSize(), is(5L));
		assertThat("incorrect user meta", oi.getUserMetaData(), is(Optional.empty()));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(false), is(MT));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(true), is(nullValue()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaData(), is(Optional.empty()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(false), is(MT));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(true), is(nullValue()));
		assertThat("incorrect ref path", oi.getReferencePath(),
				is(Arrays.asList(new Reference(4, 1, 3))));
	}

	private ObjectInformation minimalOI() {
		return ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("foo")
				.withType(TYPE)
				.withSavedDate(inst(10000))
				.withVersion(3)
				.withSavedBy(new WorkspaceUser("bar"))
				.withWorkspace(new ResolvedWorkspaceID(4, "whee", false, false))
				.withChecksum(MDFIVE)
				.withSize(5)
				.build();
	}
	
	private ObjectInformation.Builder buildFullWithAlternativeInputsSetup() {
		return ObjectInformation.getBuilder()
				.withObjectID(67)
				.withObjectName("hooray")
				.withType(new AbsoluteTypeDefId(new TypeDefName("KBG.Genome"), 1, 1))
				.withSavedDate(new Date(15000))
				.withVersion(24)
				.withSavedBy(new WorkspaceUser("myname"))
				.withWorkspace(new ResolvedWorkspaceID(1, "whoo", false, false))
				.withChecksum("uhOhNoCheckingHere")
				.withSize(890);
	}
	
	@Test
	public void buildFullWithAlternativeInputsAndNullMetadata() {
		final ObjectInformation oi = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(null)
				.withAdminUserMetadata(null)
				.build();
		buildFullWithAlternativeInputsSharedChecks(oi);
		assertThat("incorrect user meta", oi.getUserMetaData(), is(Optional.empty()));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(false), is(MT));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(true), is(nullValue()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaData(), is(Optional.empty()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(false), is(MT));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(true), is(nullValue()));
	}

	@Test
	public void buildFullWithAlternativeInputsAndEmptyMetadata() {
		final ObjectInformation oi = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(new UncheckedUserMetadata(new HashMap<>()))
				.withAdminUserMetadata(new UncheckedUserMetadata(new HashMap<>()))
				.build();
		buildFullWithAlternativeInputsSharedChecks(oi);
		assertThat("incorrect user meta", oi.getUserMetaData(), is(Optional.empty()));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(false), is(MT));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(true), is(nullValue()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaData(), is(Optional.empty()));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(false), is(MT));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(true), is(nullValue()));
	}
	
	@Test
	public void buildFullWithAlternativeInputsAndPopulatedMetadata() {
		final ObjectInformation oi = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of(
						"foo", "bar", "baz", "bat")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of(
						"role", "admin", "why", "ImBetterThanYou")))
				.build();
		buildFullWithAlternativeInputsSharedChecks(oi);
		assertThat("incorrect user meta", oi.getUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of(
						"foo", "bar", "baz", "bat")))));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(false), is(ImmutableMap.of(
				"foo", "bar", "baz", "bat")));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(true), is(ImmutableMap.of(
				"foo", "bar", "baz", "bat")));
		assertThat("incorrect admin meta", oi.getAdminUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of(
						"role", "admin", "why", "ImBetterThanYou")))));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(false), is(ImmutableMap.of(
				"role", "admin", "why", "ImBetterThanYou")));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(true), is(ImmutableMap.of(
				"role", "admin", "why", "ImBetterThanYou")));
	}
	
	@Test
	public void buildFullOverwriteMetadata() throws Exception {
		final ObjectInformation oi = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("a", "b")))
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("c", "d")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("e", "f")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("g", "h")))
				.build();
		buildFullWithAlternativeInputsSharedChecks(oi);
		assertThat("incorrect user meta", oi.getUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of("c", "d")))));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(false),
				is(ImmutableMap.of("c", "d")));
		assertThat("incorrect user meta", oi.getUserMetaDataMap(true),
				is(ImmutableMap.of("c", "d")));
		assertThat("incorrect admin meta", oi.getAdminUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of("g", "h")))));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(false),
				is(ImmutableMap.of("g", "h")));
		assertThat("incorrect admin meta", oi.getAdminUserMetaDataMap(true),
				is(ImmutableMap.of("g", "h")));
	}

	private void buildFullWithAlternativeInputsSharedChecks(final ObjectInformation oi) {
		assertThat("incorrect obj id", oi.getObjectId(), is(67L));
		assertThat("incorrect obj name", oi.getObjectName(), is("hooray"));
		assertThat("incorrect obj type", oi.getTypeString(), is("KBG.Genome-1.1"));
		assertThat("incorrect obj date", oi.getSavedDate(), is(inst(15000)));
		assertThat("incorrect obj ver", oi.getVersion(), is(24));
		assertThat("incorrect obj user", oi.getSavedBy(), is(new WorkspaceUser("myname")));
		assertThat("incorrect obj ws name", oi.getWorkspaceName(), is("whoo"));
		assertThat("incorrect obj ws id", oi.getWorkspaceId(), is(1L));
		assertThat("incorrect obj checksum", oi.getCheckSum(), is("uhOhNoCheckingHere"));
		assertThat("incorrect obj size", oi.getSize(), is(890L));
		assertThat("incorrect ref path", oi.getReferencePath(),
				is(Arrays.asList(new Reference(1, 67, 24))));
	}
	
	@Test
	public void buildFromObjectInfoMinimal() throws Exception {
		final ObjectInformation source = minimalOI();
		final ObjectInformation target = ObjectInformation.getBuilder(source).build();

		assertThat("incorrect build", target, is(source));
	}
	
	@Test
	public void buildFromObjectInfoMaximal() throws Exception {
		final ObjectInformation source = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("a", "b")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("e", "f")))
				.build();
		final ObjectInformation target = ObjectInformation.getBuilder(source).build();

		assertThat("incorrect build", target, is(source));
	}
	
	@Test
	public void buildFromObjectInfoWithRefPath() throws Exception {
		final ObjectInformation source = buildFullWithAlternativeInputsSetup()
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("a", "b")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("e", "f")))
				.build()
				.updateReferencePath(Arrays.asList(
						new Reference(3, 4, 5), new Reference(1, 67, 24)));
		final ObjectInformation target = ObjectInformation.getBuilder(source).build();

		assertThat("incorrect build", target, is(source));
		assertThat("incorrect ref path", target.getReferencePath(), is(Arrays.asList(
				new Reference(3, 4, 5), new Reference(1, 67, 24))));
	}
	
	@Test
	public void refPathImmutable() throws Exception {
		final ObjectInformation oi = ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("foo")
				.withType(TYPE)
				.withSavedDate(inst(10000))
				.withVersion(3)
				.withSavedBy(new WorkspaceUser("bar"))
				.withWorkspace(new ResolvedWorkspaceID(4, "whee", false, false))
				.withChecksum(new MD5("9a5b862b3f6969ec491ddeea83590e04"))
				.withSize(5)
				.withUserMetadata(new UncheckedUserMetadata(new HashMap<>()))
				.build();
		
		failModifyReferencePath(oi);
		final List<Reference> incomingPath = new LinkedList<>(
				Arrays.asList(new Reference(1, 1, 1), new Reference(4, 1, 3)));
		final ObjectInformation oi2 = oi.updateReferencePath(incomingPath);
		incomingPath.remove(0);
		assertThat("incorrect ref path", oi2.getReferencePath(), is(Arrays.asList(
				new Reference(1, 1, 1), new Reference(4, 1, 3))));
		failModifyReferencePath(oi2);
	}

	public void failModifyReferencePath(final ObjectInformation oi) {
		try {
			oi.getReferencePath().add(new Reference(1, 1, 1));
			fail("expected exception");
		} catch (UnsupportedOperationException e) {
			// test passes
		}
	}
	
	@Test
	public void withObjectIDFail() {
		failBuildWith(0, "foo", TYPE, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new IllegalArgumentException("id must be > 0"));
	}
	
	@Test
	public void withObjectNameFailNull() {
		failBuildWith(1, null, TYPE, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new IllegalArgumentException("name cannot be null or whitespace only"));
	}
	
	@Test
	public void withObjectNameFailWhitespace() {
		failBuildWith(1, "   \t ", TYPE, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new IllegalArgumentException("name cannot be null or whitespace only"));
	}
	
	@Test
	public void withTypeFail() {
		failBuildWith(1, "foo", null, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new NullPointerException("type"));
	}
	
	@Test
	public void withSavedDateFailInstant() {
		failBuildWith(1, "foo", TYPE, null, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new NullPointerException("date"));
	}
	
	@Test
	public void withSavedDateFailDate() {
		failBuildWith(1, "foo", TYPE, null, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "md5", 1,
				new NullPointerException("date"));
	}
	
	@Test
	public void withVersionFail() {
		failBuildWith(1, "foo", TYPE, INST, 0, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new IllegalArgumentException("version must be > 0"));
	}
	
	@Test
	public void withSavedByFail() {
		failBuildWith(1, "foo", TYPE, INST, 1, null,
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 1,
				new NullPointerException("user"));
	}
	
	@Test
	public void withWorkspaceFail() {
		failBuildWith(1, "foo", TYPE, INST, 1, new WorkspaceUser("foo"),
				null, MDFIVE, 1,
				new NullPointerException("workspace"));
	}
	
	@Test
	public void withChecksumFailMD5() {
		failBuildWith(1, "foo", TYPE, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), null, 1,
				new NullPointerException("checksum"));
	}
	
	@Test
	public void withChecksumFailStringNull() {
		failBuildWith(1, "foo", TYPE, new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), null, 1,
				new IllegalArgumentException("checksum cannot be null or whitespace only"));
	}
	
	@Test
	public void withChecksumFailStringWhitespace() {
		failBuildWith(1, "foo", TYPE, new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "   \t   ", 1,
				new IllegalArgumentException("checksum cannot be null or whitespace only"));
	}
	
	@Test
	public void withSizeFail() {
		failBuildWith(1, "foo", TYPE, INST, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), MDFIVE, 0,
				new IllegalArgumentException("size must be > 0"));
	}
	
	private void failBuildWith(
			final long id,
			final String name,
			final AbsoluteTypeDefId type,
			final Instant saved,
			final int version,
			final WorkspaceUser savedBy,
			final ResolvedWorkspaceID ws,
			final MD5 chksum,
			final long size,
			final Exception exp) {
		try {
			ObjectInformation.getBuilder()
					.withObjectID(id)
					.withObjectName(name)
					.withType(type)
					.withSavedDate(saved)
					.withVersion(version)
					.withSavedBy(savedBy)
					.withWorkspace(ws)
					.withChecksum(chksum)
					.withSize(size);
			fail("created bad object info");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, exp);
		}
	}
	
	private void failBuildWith(
			final long id,
			final String name,
			final AbsoluteTypeDefId type,
			final Date saved,
			final int version,
			final WorkspaceUser savedBy,
			final ResolvedWorkspaceID ws,
			final String chksum,
			final long size,
			final Exception exp) {
		try {
			ObjectInformation.getBuilder()
					.withObjectID(id)
					.withObjectName(name)
					.withType(type)
					.withSavedDate(saved)
					.withVersion(version)
					.withSavedBy(savedBy)
					.withWorkspace(ws)
					.withChecksum(chksum)
					.withSize(size);
			fail("created bad object info");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void buildFail() throws Exception {
		final WorkspaceUser u = new WorkspaceUser("foo");
		final ResolvedWorkspaceID r = new ResolvedWorkspaceID(1, "f", false, false);
		failBuildMissingFields(null, "n", TYPE, INST, 1, u, r, MDFIVE, 1L);
		failBuildMissingFields(1L, null, TYPE, INST, 1, u, r, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", null, INST, 1, u, r, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", TYPE, null, 1, u, r, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", TYPE, INST, null, u, r, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", TYPE, INST, 1, null, r, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", TYPE, INST, 1, u, null, MDFIVE, 1L);
		failBuildMissingFields(1L, "n", TYPE, INST, 1, u, r, null, 1L);
		failBuildMissingFields(1L, "n", TYPE, INST, 1, u, r, MDFIVE, null);
	}
	
	private void failBuildMissingFields(
			final Long id,
			final String name,
			final AbsoluteTypeDefId type,
			final Instant saved,
			final Integer version,
			final WorkspaceUser savedBy,
			final ResolvedWorkspaceID ws,
			final MD5 chksum,
			final Long size) {
		final ObjectInformation.Builder b = ObjectInformation.getBuilder();
		if (id != null) {
			b.withObjectID(id);
		}
		if (name != null) {
			b.withObjectName(name);
		}
		if (type != null) {
			b.withType(type);
		}
		if (saved != null) {
			b.withSavedDate(saved);
		}
		if (version != null) {
			b.withVersion(version);
		}
		if (savedBy != null) {
			b.withSavedBy(savedBy);
		}
		if (ws != null) {
			b.withWorkspace(ws);
		}
		if (chksum != null) {
			b.withChecksum(chksum);
		}
		if (size != null) {
			b.withSize(size);
		}
		try {
			b.build();
			fail("expected exception");
		} catch (IllegalArgumentException e) {
			assertExceptionCorrect(e, new IllegalArgumentException(
					"One or more of the required arguments are not set. "
					+ "Please check the documentation for the builder."));
		}
	}
	
	@Test
	public void buildFailFromObjectInformation() throws Exception {
		try {
			ObjectInformation.getBuilder(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("info"));
		}
	}

	@Test
	public void refPath() {
		final ObjectInformation oi = ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("foo")
				.withType(new AbsoluteTypeDefId(new TypeDefName("type.t"), 3, 7))
				.withSavedDate(inst(10000))
				.withVersion(3)
				.withSavedBy(new WorkspaceUser("bar"))
				.withWorkspace(new ResolvedWorkspaceID(4, "whee", false, false))
				.withChecksum(new MD5("9a5b862b3f6969ec491ddeea83590e04"))
				.withSize(5)
				.withUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("x", "y")))
				.withAdminUserMetadata(new UncheckedUserMetadata(ImmutableMap.of("w", "z")))
				.build();
		final ObjectInformation oi2 = oi.updateReferencePath(
				Arrays.asList(new Reference(7, 7, 7), new Reference(4, 1, 3)));
		assertThat("incorrect original reference path", oi.getReferencePath(),
				is(Arrays.asList(new Reference(4, 1, 3))));
		assertThat("incorrect new reference path", oi2.getReferencePath(),
				is(Arrays.asList(new Reference(7, 7, 7), new Reference(4, 1, 3))));
		
		// check that only the ref path changed
		assertThat("incorrect obj id", oi2.getObjectId(), is(1L));
		assertThat("incorrect obj name", oi2.getObjectName(), is("foo"));
		assertThat("incorrect obj type", oi2.getTypeString(), is("type.t-3.7"));
		assertThat("incorrect obj date", oi2.getSavedDate(), is(inst(10000)));
		assertThat("incorrect obj ver", oi2.getVersion(), is(3));
		assertThat("incorrect obj user", oi2.getSavedBy(), is(new WorkspaceUser("bar")));
		assertThat("incorrect obj ws name", oi2.getWorkspaceName(), is("whee"));
		assertThat("incorrect obj ws id", oi2.getWorkspaceId(), is(4L));
		assertThat("incorrect obj checksum", oi2.getCheckSum(),
				is("9a5b862b3f6969ec491ddeea83590e04"));
		assertThat("incorrect obj size", oi2.getSize(), is(5L));
		assertThat("incorrect user meta", oi2.getUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of("x", "y")))));
		assertThat("incorrect user meta", oi2.getUserMetaDataMap(false),
				is(ImmutableMap.of("x", "y")));
		assertThat("incorrect user meta", oi2.getUserMetaDataMap(true),
				is(ImmutableMap.of("x", "y")));
		assertThat("incorrect admin meta", oi2.getAdminUserMetaData(),
				is(Optional.of(new UncheckedUserMetadata(ImmutableMap.of("w", "z")))));
		assertThat("incorrect admin meta", oi2.getAdminUserMetaDataMap(false),
				is(ImmutableMap.of("w", "z")));
		assertThat("incorrect admin meta", oi2.getAdminUserMetaDataMap(true),
				is(ImmutableMap.of("w", "z")));
	}
	
	@Test
	public void failUpdateRefPathNull() {
		failUpdateRefPath(null, new IllegalArgumentException("refpath cannot be null or empty"));
	}
	
	@Test
	public void failUpdateRefPathEmpty() {
		failUpdateRefPath(Collections.emptyList(), new IllegalArgumentException(
				"refpath cannot be null or empty"));
	}
	
	@Test
	public void failUpdateRefPathNullInPath() {
		failUpdateRefPath(Arrays.asList(new Reference(7, 7, 7), null, new Reference(3, 3, 1)),
				new NullPointerException("refpath cannot contain nulls"));
	}
	
	@Test
	public void failUpdateRefPathMisMatch() {
		failUpdateRefPath(Arrays.asList(new Reference(7, 7, 7), new Reference(3, 3, 1)),
				new IllegalArgumentException(
						"refpath must end with the same reference as the current refpath"));
	}
	
	private void failUpdateRefPath(final List<Reference> refpath, final Exception expected) {
		final ObjectInformation oi = ObjectInformation.getBuilder()
				.withObjectID(1)
				.withObjectName("foo")
				.withType(new AbsoluteTypeDefId(new TypeDefName("type.t"), 3, 7))
				.withSavedDate(inst(10000))
				.withVersion(3)
				.withSavedBy(new WorkspaceUser("bar"))
				.withWorkspace(new ResolvedWorkspaceID(4, "whee", false, false))
				.withChecksum(new MD5("9a5b862b3f6969ec491ddeea83590e04"))
				.withSize(5)
				.withUserMetadata(new UncheckedUserMetadata(new HashMap<>()))
				.build();
		try {
			oi.updateReferencePath(refpath);
			fail("updated bad refpath");
		} catch (Exception got) {
			assertExceptionCorrect(got, expected);
		}
	}
}
