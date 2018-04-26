package us.kbase.workspace.test.database;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.workspace.database.DependencyStatus;

public class DependencyStatusTest {
	
	@Test
	public void equals() {
		EqualsVerifier.forClass(DependencyStatus.class).usingGetClass().verify();
	}
	
	@Test
	public void constructTrue() {
		final DependencyStatus dep = new DependencyStatus(true, "status", "my name", "my version");
		
		assertThat("incorrect ok", dep.isOk(), is(true));
		assertThat("incorrect status", dep.getStatus(), is("status"));
		assertThat("incorrect my name", dep.getName(), is("my name"));
		assertThat("incorrect my version", dep.getVersion(), is("my version"));
		assertThat("incorrect toString", dep.toString(), is(
				"DependencyStatus [ok=true, status=status, name=my name, version=my version]"));
	}
	
	@Test
	public void constructFalseWithNulls() {
		final DependencyStatus dep = new DependencyStatus(false, null, null, null);
		
		assertThat("incorrect ok", dep.isOk(), is(false));
		assertThat("incorrect status", dep.getStatus(), is((String) null));
		assertThat("incorrect my name", dep.getName(), is((String) null));
		assertThat("incorrect my version", dep.getVersion(), is((String) null));
		assertThat("incorrect toString", dep.toString(), is(
				"DependencyStatus [ok=false, status=null, name=null, version=null]"));
	}

}
