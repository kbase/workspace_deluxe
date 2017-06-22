package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class ObjectInformationTest {

	@Test
	public void constructor() {
		final Date d = new Date();
		final Map<String, String> meta = new HashMap<>();
		final ObjectInformation oi = new ObjectInformation(1L, "foo", "type", d, 3,
				new WorkspaceUser("bar"), new ResolvedWorkspaceID(4, "whee", false, false),
				"sum", 5L,
				new UncheckedUserMetadata(meta));
		assertThat("incorrect obj id", oi.getObjectId(), is(1L));
		assertThat("incorrect obj name", oi.getObjectName(), is("foo"));
		assertThat("incorrect obj type", oi.getTypeString(), is("type"));
		assertThat("incorrect obj date", oi.getSavedDate(), is(d));
		assertThat("incorrect obj ver", oi.getVersion(), is(3));
		assertThat("incorrect obj user", oi.getSavedBy(), is(new WorkspaceUser("bar")));
		assertThat("incorrect obj ws name", oi.getWorkspaceName(), is("whee"));
		assertThat("incorrect obj ws id", oi.getWorkspaceId(), is(4L));
		assertThat("incorrect obj checksum", oi.getCheckSum(), is("sum"));
		assertThat("incorrect obj size", oi.getSize(), is(5L));
		assertThat("incorrect obj meta", oi.getUserMetaData(),
				is(new UncheckedUserMetadata(meta)));
		assertThat("incorrect ref path", oi.getReferencePath(),
				is(Arrays.asList(new Reference(4, 1, 3))));
	}
	
	@Test
	public void failConstructID() {
		failConstruct(0, "foo", "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("id must be > 0"));
	}
	
	@Test
	public void failConstructNameNull() {
		failConstruct(1, null, "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("name is null or empty"));
	}
	
	@Test
	public void failConstructNameEmpty() {
		failConstruct(1, "", "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("name is null or empty"));
	}
	
	@Test
	public void failConstructTypeNull() {
		failConstruct(1, "foo", null, new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("typeString is null or empty"));
	}
	
	@Test
	public void failConstructTypeEmpty() {
		failConstruct(1, "foo", "", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("typeString is null or empty"));
	}
	
	@Test
	public void failConstructDate() {
		failConstruct(1, "foo", "foo", null, 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new NullPointerException("savedDate"));
	}
	
	@Test
	public void failConstructVersion() {
		failConstruct(1, "foo", "foo", new Date(), 0, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("version must be > 0"));
	}
	
	@Test
	public void failConstructSavedBy() {
		failConstruct(1, "foo", "foo", new Date(), 1, null,
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new NullPointerException("savedBy"));
	}
	
	@Test
	public void failConstructWorkspace() {
		failConstruct(1, "foo", "foo", new Date(), 1, new WorkspaceUser("foo"),
				null, "foo", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new NullPointerException("workspaceID"));
	}
	
	@Test
	public void failConstructChksumNull() {
		failConstruct(1, "foo", "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), null, 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("chksum is null or empty"));
	}
	
	@Test
	public void failConstructChksumEmpty() {
		failConstruct(1, "foo", "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "", 1,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("chksum is null or empty"));
	}
	
	@Test
	public void failConstructSize() {
		failConstruct(1, "foo", "foo", new Date(), 1, new WorkspaceUser("foo"),
				new ResolvedWorkspaceID(1, "foo", false, false), "foo", 0,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null),
				new IllegalArgumentException("size must be > 0"));
	}
	
	private void failConstruct(
			final long id,
			final String name,
			final String type,
			final Date savedDate,
			final int version,
			final WorkspaceUser savedBy,
			final ResolvedWorkspaceID ws,
			final String chksum,
			final long size,
			final UncheckedUserMetadata meta,
			final Exception exp) {
		try {
			new ObjectInformation(id, name, type, savedDate, version, savedBy, ws, chksum, size, meta);
			fail("created bad object info");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, exp);
		}
	}

	@Test
	public void refPath() {
		final ObjectInformation oi = new ObjectInformation(1L, "foo", "type", new Date(), 3,
				new WorkspaceUser("bar"), new ResolvedWorkspaceID(4, "whee", false, false),
				"sum", 5L,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null));
		final ObjectInformation oi2 = oi.updateReferencePath(
				Arrays.asList(new Reference(7, 7, 7), new Reference(4, 1, 3)));
		assertThat("incorrect original reference path", oi.getReferencePath(),
				is(Arrays.asList(new Reference(4, 1, 3))));
		assertThat("incorrect new reference path", oi2.getReferencePath(),
				is(Arrays.asList(new Reference(7, 7, 7), new Reference(4, 1, 3))));
		
	}
	
	@Test
	public void failUpdateRefPathNull() {
		failUpdateRefPath(null, "refpath cannot be null or empty");
	}
	
	@Test
	public void failUpdateRefPathEmpty() {
		failUpdateRefPath(new LinkedList<Reference>(), "refpath cannot be null or empty");
	}
	
	@Test
	public void failUpdateRefPathMisMatch() {
		failUpdateRefPath(Arrays.asList(new Reference(7, 7, 7), new Reference(3, 3, 1)),
				"refpath must end with the same reference as the current refpath");
	}
	
	private void failUpdateRefPath(final List<Reference> refpath, final String exp) {
		final ObjectInformation oi = new ObjectInformation(1L, "foo", "type", new Date(), 3,
				new WorkspaceUser("bar"), new ResolvedWorkspaceID(4, "whee", false, false),
				"sum", 5L,
				new UncheckedUserMetadata((WorkspaceUserMetadata) null));
		try {
			oi.updateReferencePath(refpath);
			fail("updated bad refpath");
		} catch (IllegalArgumentException e) {
			assertThat("incorrect exception message", e.getMessage(), is(exp));
		}
	}
}
