package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.assertExceptionCorrect;
import static us.kbase.test.common.TestCommon.set;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.workspace.database.MetadataUpdate;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class MetadataUpdateTest {
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(MetadataUpdate.class).usingGetClass().verify();
	}
	
	@Test
	public void constructNulls() throws Exception {
		final MetadataUpdate mu = new MetadataUpdate(null, null);
		assertThat("incorrect meta", mu.getMeta(), is(Optional.empty()));
		assertThat("incorrect remove", mu.getToRemove(), is(Optional.empty()));
		assertThat("incorrect hasUpdate", mu.hasUpdate(), is(false));
	}
	
	@Test
	public void constructEmpties() throws Exception {
		final MetadataUpdate mu = new MetadataUpdate(new WorkspaceUserMetadata(), set());
		assertThat("incorrect meta", mu.getMeta(), is(Optional.empty()));
		assertThat("incorrect remove", mu.getToRemove(), is(Optional.empty()));
		assertThat("incorrect hasUpdate", mu.hasUpdate(), is(false));
	}

	@Test
	public void constructWithMeta() throws Exception {
		final MetadataUpdate mu = new MetadataUpdate(
				new WorkspaceUserMetadata(ImmutableMap.of("a", "b")), null);
		assertThat("incorrect meta", mu.getMeta(), is(Optional.of(
				new WorkspaceUserMetadata(ImmutableMap.of("a", "b")))));
		assertThat("incorrect remove", mu.getToRemove(), is(Optional.empty()));
		assertThat("incorrect hasUpdate", mu.hasUpdate(), is(true));
	}
	
	@Test
	public void constructWithRemove() throws Exception {
		final MetadataUpdate mu = new MetadataUpdate(null, Arrays.asList("foo", "bar"));
		assertThat("incorrect meta", mu.getMeta(), is(Optional.empty()));
		assertThat("incorrect remove", mu.getToRemove(), is(Optional.of(set("bar", "foo"))));
		assertThat("incorrect hasUpdate", mu.hasUpdate(), is(true));
	}
	
	@Test
	public void constructWithBoth() throws Exception {
		final MetadataUpdate mu = new MetadataUpdate(
				new WorkspaceUserMetadata(ImmutableMap.of("a", "b")), set("baz", "bat"));
		assertThat("incorrect meta", mu.getMeta(), is(Optional.of(
				new WorkspaceUserMetadata(ImmutableMap.of("a", "b")))));
		assertThat("incorrect remove", mu.getToRemove(), is(Optional.of(set("baz", "bat"))));
		assertThat("incorrect hasUpdate", mu.hasUpdate(), is(true));
	}
	
	@Test
	public void constructFailNullInRemove() throws Exception {
		try {
			new MetadataUpdate(null, Arrays.asList("foo", null));
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new NullPointerException(
					"null metadata keys are not allowed in the remove parameter"));
		}
		
	}
}
