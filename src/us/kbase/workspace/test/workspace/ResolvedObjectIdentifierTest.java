package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ResolvedObjectID;
import us.kbase.workspace.database.ResolvedObjectIDNoVer;
import us.kbase.workspace.database.ResolvedWorkspaceID;

public class ResolvedObjectIdentifierTest {

	private static final ResolvedWorkspaceID RWSI = new ResolvedWorkspaceID(2, "foo", true, false);

	@Test
	public void fullEquals() {
		EqualsVerifier.forClass(ResolvedObjectID.class).usingGetClass().verify();
	}
	
	@Test
	public void constructFullID() {
		final ResolvedObjectID id = new ResolvedObjectID(RWSI, 6, 7, "foo", false);
		assertThat("incorrect ws id", id.getWorkspaceIdentifier(), is(RWSI));
		assertThat("incorrect id", id.getId(), is(6L));
		assertThat("incorrect version", id.getVersion(), is(7));
		assertThat("incorrect name", id.getName(), is("foo"));
		assertThat("incorrect del", id.isDeleted(), is(false));
		assertThat("incorrect ref", id.getReference(), is(new Reference("2/6/7")));
		assertThat("incorrect toString", id.toString(),
				is("ResolvedObjectID [rwsi=ResolvedWorkspaceID [id=2, wsname=foo, " +
						"locked=true, deleted=false], name=foo, id=6, version=7, deleted=false]"));
	}
	
	@Test
	public void constructFullIDFail() {
		failConstructFull(null, 1, 1, "foo", new NullPointerException("rwsi"));
		failConstructFull(RWSI, 0, 1, "foo", new IllegalArgumentException("id must be > 0"));
		failConstructFull(RWSI, 1, 0, "foo", new IllegalArgumentException("version must be > 0"));
		failConstructFull(RWSI, 1, 0, "fo*o",
				new IllegalArgumentException("Illegal character in object name fo*o: *"));
	}
	
	private void failConstructFull(
			final ResolvedWorkspaceID rwsi,
			final long id,
			final int version,
			final String name,
			final Exception e) {
		try {
			new ResolvedObjectID(rwsi, id, version, name, true);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void noVerEquals() {
		EqualsVerifier.forClass(ResolvedObjectIDNoVer.class).usingGetClass().verify();
	}
	
	@Test
	public void constructNoVer() {
		final ResolvedObjectIDNoVer id = new ResolvedObjectIDNoVer(RWSI, 6, "foo", false);
		assertThat("incorrect ws id", id.getWorkspaceIdentifier(), is(RWSI));
		assertThat("incorrect id", id.getId(), is(6L));
		assertThat("incorrect name", id.getName(), is("foo"));
		assertThat("incorrect del", id.isDeleted(), is(false));
		assertThat("incorrect toString", id.toString(),
				is("ResolvedObjectIDNoVer [rwsi=ResolvedWorkspaceID [id=2, wsname=foo, " +
						"locked=true, deleted=false], name=foo, id=6, deleted=false]"));
	}
	
	@Test
	public void constructNoVerFail() {
		failConstructNoVer(null, 5, "foo", new NullPointerException("rwsi"));
		failConstructNoVer(RWSI, 0, "foo", new IllegalArgumentException("id must be > 0"));
		failConstructFull(RWSI, 1, 0, "fo*o",
				new IllegalArgumentException("Illegal character in object name fo*o: *"));
	}
	
	private void failConstructNoVer(
			final ResolvedWorkspaceID rwsi,
			final long id,
			final String name,
			final Exception e) {
		try {
			new ResolvedObjectIDNoVer(rwsi, id, name, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
			
	@Test
	public void constructNoVerFromWithVer() {
		final ResolvedObjectID fromid = new ResolvedObjectID(RWSI, 6, 7, "foo", false);
		final ResolvedObjectIDNoVer id = new ResolvedObjectIDNoVer(fromid);
		assertThat("incorrect ws id", id.getWorkspaceIdentifier(), is(RWSI));
		assertThat("incorrect id", id.getId(), is(6L));
		assertThat("incorrect name", id.getName(), is("foo"));
		assertThat("incorrect del", id.isDeleted(), is(false));
		assertThat("incorrect toString", id.toString(),
				is("ResolvedObjectIDNoVer [rwsi=ResolvedWorkspaceID [id=2, wsname=foo, " +
						"locked=true, deleted=false], name=foo, id=6, deleted=false]"));
	}
	
	@Test
	public void constructNoVerFromWithVerFail() {
		try {
			new ResolvedObjectIDNoVer(null);
			fail("expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, new NullPointerException("roid"));
		}
	}
}
