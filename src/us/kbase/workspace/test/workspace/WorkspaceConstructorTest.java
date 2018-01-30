package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.listener.WorkspaceEventListener;

public class WorkspaceConstructorTest {

	@Test
	public void construct3() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		
		final Workspace ws = new Workspace(db, cfg, tv);
		
		assertThat("incorrect resource cfg", ws.getResourceConfig(), is(cfg));
		assertThat("incorrect max search count", ws.getMaximumObjectSearchCount(), is(10000));
		
		// not worth the trouble to run something to check that the db and val are set. If not,
		// lots of other tests will fail
	}
	
	@Test
	public void construct4() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, Arrays.asList(l));
		
		assertThat("incorrect resource cfg", ws.getResourceConfig(), is(cfg));
		assertThat("incorrect max search count", ws.getMaximumObjectSearchCount(), is(10000));
		
		// not worth the trouble to run something to check that the db, val, and l are set. If not,
		// lots of other tests will fail
	}
	
	@Test
	public void construct3Fail() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		
		failConstruct(null, cfg, tv, new NullPointerException("db cannot be null"));
		failConstruct(db, null, tv, new NullPointerException("cfg cannot be null"));
		failConstruct(db, cfg, null, new NullPointerException("validator cannot be null"));
	}
	
	@Test
	public void construct4Fail() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		final WorkspaceEventListener wel = mock(WorkspaceEventListener.class);
		final List<WorkspaceEventListener> l = Arrays.asList(wel);
		
		
		failConstruct(null, cfg, tv, l, new NullPointerException("db cannot be null"));
		failConstruct(db, null, tv, l, new NullPointerException("cfg cannot be null"));
		failConstruct(db, cfg, null, l, new NullPointerException("validator cannot be null"));
		failConstruct(db, cfg, tv, null, new NullPointerException("listeners"));
		failConstruct(db, cfg, tv, Arrays.asList(wel, null),
				new NullPointerException("null item in listeners"));
	}

	private void failConstruct(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator tv,
			final Exception e) {
		try {
			new Workspace(db, cfg, tv);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failConstruct(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator tv,
			final List<WorkspaceEventListener> l,
			final Exception e) {
		try {
			new Workspace(db, cfg, tv, l);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
}
