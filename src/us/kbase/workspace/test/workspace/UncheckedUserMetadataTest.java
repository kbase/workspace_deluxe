package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class UncheckedUserMetadataTest {
	
	private static final Map<String, String> MT = Collections.emptyMap();

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(UncheckedUserMetadata.class).usingGetClass().verify();
	}
	
	@Test
	public void constructor() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		assertThat("stored correct metadata", uum.getMetadata(), is(m));
		assertThat("isEmpty incorrect", uum.isEmpty(), is(false));
		Map<String, String> origm = new HashMap<String, String>(m);
		m.put("baz", "bar");
		assertThat("changing constructor map doesn't change metadata contents",
				uum.getMetadata(), is(origm));
		
		m.clear();
		m.put("foo", "bar");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		uum = new UncheckedUserMetadata(wum);
		assertThat("stored correct metadata", uum.getMetadata(), is(m));
		assertThat("isEmpty incorrect", uum.isEmpty(), is(false));
		origm = new HashMap<String, String>(m);
		m.put("baz", "bar");
		assertThat("changing constructor map doesn't change metadata contents",
				uum.getMetadata(), is(origm));
		
		uum = new UncheckedUserMetadata((Map<String, String>) null);
		assertThat("passing null map = empty metadata", uum.getMetadata(), is(MT));
		assertThat("isEmpty incorrect", uum.isEmpty(), is(true));
		
		uum = new UncheckedUserMetadata((WorkspaceUserMetadata) null);
		assertThat("passing null workspace meta = empty metadata", uum.getMetadata(), is(MT));
		assertThat("isEmpty incorrect", uum.isEmpty(), is(true));
		
	}
	
	@Test
	public void getMetadataImmutable() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		Map<String, String> out = uum.getMetadata();
		try {
			out.put("whee", "whoo");
			fail("expected unsupported exception");
		} catch (UnsupportedOperationException uoe) {
			//pass
		}
	}
	
	@Test
	public void string() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		assertThat("correct toString()", uum.toString(),
				is("UncheckedUserMetadata [metadata={foo=bar}]"));
	}
	
}
