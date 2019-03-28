package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceUser;

public class ProvenanceTest {
	
	//TODO TEST add more tests
	
	@Test
	public void setAndGetWorkspaceID() throws Exception {
		final Provenance p = new Provenance(new WorkspaceUser("u"));
		
		p.setWorkspaceID(1L);
		assertThat("incorrect wsid", p.getWorkspaceID(), is(1L));
		
		p.setWorkspaceID(null);
		assertThat("incorrect wsid", p.getWorkspaceID(), is(nullValue()));
	}
	
	@Test
	public void failSetWorkspaceID() throws Exception {
		try {
			new Provenance(new WorkspaceUser("u")).setWorkspaceID(0L);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got,
					new IllegalArgumentException("wsid must be > 0"));
		}
	}

}
