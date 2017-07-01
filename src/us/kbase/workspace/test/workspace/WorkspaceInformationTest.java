package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertThat;

import java.time.Instant;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceInformation.Builder;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class WorkspaceInformationTest {
	
	final static UncheckedUserMetadata MT_META = new UncheckedUserMetadata(
			new WorkspaceUserMetadata());
	
	@Test
	public void equals() {
		EqualsVerifier.forClass(WorkspaceInformation.class).usingGetClass().verify();
	}
	
	@Test
	public void buildMinimal() {
		final WorkspaceInformation wi = WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ)
				.build();
		
		assertThat("incorrect ID", wi.getId(), is(2L));
		assertThat("incorrect name", wi.getName(), is("foo"));
		assertThat("incorrect max obj id", wi.getMaximumObjectID(), is(20L));
		assertThat("incorrect mod date", wi.getModDate(), is(Instant.ofEpochMilli(10000)));
		assertThat("incorrect owner", wi.getOwner(), is(new WorkspaceUser("foobar")));
		assertThat("incorrect user perm", wi.getUserPermission(), is(Permission.READ));
		assertThat("incorrect global read", wi.isGloballyReadable(), is(false));
		assertThat("incorrect locked", wi.isLocked(), is(false));
		assertThat("incorrect meta", wi.getUserMeta(), is(MT_META));
		assertThat("incorrect lock state", wi.getLockState(), is("unlocked"));
		assertThat("incorrect toString", wi.toString(), is(
				"WorkspaceInformation [id=2, name=foo, owner=User [user=foobar], " +
				"modDate=1970-01-01T00:00:10Z, maxObjectID=20, userPermission=READ, " +
				"globalRead=false, locked=false, usermeta=UncheckedUserMetadata [metadata={}]]"));
	}
	
	@Test
	public void buildMaximal() {
		final UncheckedUserMetadata meta = new UncheckedUserMetadata(
				ImmutableMap.of("foo", "bar"));
		final WorkspaceInformation wi = WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ)
				.withLocked(true)
				.withGlobalRead(true)
				.withUserMetadata(meta)
				.build();
		
		assertThat("incorrect ID", wi.getId(), is(2L));
		assertThat("incorrect name", wi.getName(), is("foo"));
		assertThat("incorrect max obj id", wi.getMaximumObjectID(), is(20L));
		assertThat("incorrect mod date", wi.getModDate(), is(Instant.ofEpochMilli(10000)));
		assertThat("incorrect owner", wi.getOwner(), is(new WorkspaceUser("foobar")));
		assertThat("incorrect user perm", wi.getUserPermission(), is(Permission.READ));
		assertThat("incorrect global read", wi.isGloballyReadable(), is(true));
		assertThat("incorrect locked", wi.isLocked(), is(true));
		assertThat("incorrect meta", wi.getUserMeta(), is(meta));
		assertThat("incorrect lock state", wi.getLockState(), is("published"));
		assertThat("incorrect toString", wi.toString(), is(
				"WorkspaceInformation [id=2, name=foo, owner=User [user=foobar], " +
				"modDate=1970-01-01T00:00:10Z, maxObjectID=20, userPermission=READ, " +
				"globalRead=true, locked=true, usermeta=UncheckedUserMetadata " + 
				"[metadata={foo=bar}]]"));
	}
	
	@Test
	public void buildLocked() {
		final UncheckedUserMetadata meta = new UncheckedUserMetadata(
				ImmutableMap.of("foo", "bar"));
		final WorkspaceInformation wi = WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ)
				.withLocked(true)
				.withGlobalRead(false)
				.withUserMetadata(meta)
				.build();
		
		assertThat("incorrect ID", wi.getId(), is(2L));
		assertThat("incorrect name", wi.getName(), is("foo"));
		assertThat("incorrect max obj id", wi.getMaximumObjectID(), is(20L));
		assertThat("incorrect mod date", wi.getModDate(), is(Instant.ofEpochMilli(10000)));
		assertThat("incorrect owner", wi.getOwner(), is(new WorkspaceUser("foobar")));
		assertThat("incorrect user perm", wi.getUserPermission(), is(Permission.READ));
		assertThat("incorrect global read", wi.isGloballyReadable(), is(false));
		assertThat("incorrect locked", wi.isLocked(), is(true));
		assertThat("incorrect meta", wi.getUserMeta(), is(meta));
		assertThat("incorrect lock state", wi.getLockState(), is("locked"));
		assertThat("incorrect toString", wi.toString(), is(
				"WorkspaceInformation [id=2, name=foo, owner=User [user=foobar], " +
				"modDate=1970-01-01T00:00:10Z, maxObjectID=20, userPermission=READ, " +
				"globalRead=false, locked=true, usermeta=UncheckedUserMetadata " + 
				"[metadata={foo=bar}]]"));
	}
	
	@Test
	public void failBuildID() {
		failBuild(WorkspaceInformation.getBuilder()
//				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ),
				new NullPointerException("id"));
	}
	
	@Test
	public void failBuildName() {
		failBuild(WorkspaceInformation.getBuilder()
				.withID(2)
//				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ),
				new NullPointerException("name"));
	}
	
	@Test
	public void failBuildObjID() {
		failBuild(WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
//				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ),
				new NullPointerException("maxObjectID"));
	}
	
	@Test
	public void failBuildModDate() {
		failBuild(WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
//				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ),
				new NullPointerException("modDate"));
	}
	
	@Test
	public void failBuildOwner() {
		failBuild(WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
//				.withOwner(new WorkspaceUser("foobar"))
				.withUserPermission(Permission.READ),
				new NullPointerException("owner"));
	}
	
	@Test
	public void failBuildUserPerm() {
		failBuild(WorkspaceInformation.getBuilder()
				.withID(2)
				.withName("foo")
				.withMaximumObjectID(20)
				.withModificationDate(Instant.ofEpochMilli(10000))
				.withOwner(new WorkspaceUser("foobar")),
//				.withUserPermission(Permission.READ),
				new NullPointerException("userPermission"));
	}
	
	private void failBuild(final Builder b, final Exception e) {
		try {
			b.build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void failSetID() {
		try {
			WorkspaceInformation.getBuilder().withID(0);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException("id must be > 0"));
		}
	}
	
	@Test
	public void failSetName() {
		try {
			WorkspaceInformation.getBuilder().withName(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"name cannot be null or the empty string"));
		}
		try {
			//TODO CODE add whitespace when java common checks string with trim()
			WorkspaceInformation.getBuilder().withName("");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"name cannot be null or the empty string"));
		}
	}
	
	@Test
	public void failSetOwner() {
		try {
			WorkspaceInformation.getBuilder().withOwner(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("owner"));
		}
	}
	
	@Test
	public void failSetModDate() {
		try {
			WorkspaceInformation.getBuilder().withModificationDate(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("modDate"));
		}
	}
	
	@Test
	public void failSetMaxObjID() {
		try {
			WorkspaceInformation.getBuilder().withMaximumObjectID(-1);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"max id must be at least 0"));
		}
	}
	
	@Test
	public void failSetUserPermission() {
		try {
			WorkspaceInformation.getBuilder().withUserPermission(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("perm"));
		}
	}
	
	@Test
	public void failSetMeta() {
		try {
			WorkspaceInformation.getBuilder().withUserMetadata(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("meta"));
		}
	}

}
