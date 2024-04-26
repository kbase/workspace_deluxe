package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.listener.WorkspaceEventListener;

public class WorkspaceConstructorTest {

	@Test
	public void construct4() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		
		final Workspace ws = new Workspace(db, cfg, tv, tfm);
		
		assertThat("incorrect resource cfg", ws.getResourceConfig(), is(cfg));
		assertThat("incorrect max search count", ws.getMaximumObjectSearchCount(), is(10000));
		assertThat("incorrect tfm", ws.getTempFilesManager(), is(tfm));
		
		// not worth the trouble to run something to check that the db and val are set. If not,
		// lots of other tests will fail
	}
	
	@Test
	public void construct5() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		final WorkspaceEventListener l = mock(WorkspaceEventListener.class);
		
		final Workspace ws = new Workspace(db, cfg, tv, tfm, Arrays.asList(l));
		
		assertThat("incorrect resource cfg", ws.getResourceConfig(), is(cfg));
		assertThat("incorrect max search count", ws.getMaximumObjectSearchCount(), is(10000));
		assertThat("incorrect tfm", ws.getTempFilesManager(), is(tfm));
		
		// not worth the trouble to run something to check that the db, val, and l are set. If not,
		// lots of other tests will fail
	}
	
	@Test
	public void construct4Fail() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		
		failConstruct(null, cfg, tv, tfm, new NullPointerException("db"));
		failConstruct(db, null, tv, tfm, new NullPointerException("cfg"));
		failConstruct(db, cfg, null, tfm, new NullPointerException("validator"));
		failConstruct(db, cfg, tv, null, new NullPointerException("tfm"));
	}
	
	@Test
	public void construct5Fail() throws Exception {
		final WorkspaceDatabase db = mock(WorkspaceDatabase.class);
		final TypedObjectValidator tv = mock(TypedObjectValidator.class);
		final ResourceUsageConfiguration cfg = new ResourceUsageConfigurationBuilder()
				.withMaxObjectSize(3).build();
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final WorkspaceEventListener wel = mock(WorkspaceEventListener.class);
		final List<WorkspaceEventListener> l = Arrays.asList(wel);
		
		
		failConstruct(null, cfg, tv, tfm, l, new NullPointerException("db"));
		failConstruct(db, null, tv, tfm, l, new NullPointerException("cfg"));
		failConstruct(db, cfg, null, tfm, l, new NullPointerException("validator"));
		failConstruct(db, cfg, tv, null, l, new NullPointerException("tfm"));
		failConstruct(db, cfg, tv, tfm, null, new NullPointerException("listeners"));
		failConstruct(db, cfg, tv, tfm, Arrays.asList(wel, null),
				new NullPointerException("null item in listeners"));
	}

	private void failConstruct(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator tv,
			final TempFilesManager tfm,
			final Exception e) {
		try {
			new Workspace(db, cfg, tv, tfm);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	private void failConstruct(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator tv,
			final TempFilesManager tfm,
			final List<WorkspaceEventListener> l,
			final Exception e) {
		try {
			new Workspace(db, cfg, tv, tfm, l);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
}
