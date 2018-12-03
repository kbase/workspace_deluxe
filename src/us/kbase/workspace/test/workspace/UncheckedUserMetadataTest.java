package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import us.kbase.workspace.database.UncheckedUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class UncheckedUserMetadataTest {

	@Test
	public void constructor() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		assertThat("stored correct metadata", uum.getMetadata(), is(m));
		Map<String, String> origm = new HashMap<String, String>(m);
		m.put("baz", "bar");
		assertThat("changing constructor map doesn't change metadata contents",
				uum.getMetadata(), is(origm));
		
		m.clear();
		m.put("foo", "bar");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		uum = new UncheckedUserMetadata(wum);
		assertThat("stored correct metadata", uum.getMetadata(), is(m));
		origm = new HashMap<String, String>(m);
		m.put("baz", "bar");
		assertThat("changing constructor map doesn't change metadata contents",
				uum.getMetadata(), is(origm));
		
		Map<String, String> mt = new HashMap<String, String>();
		m = null;
		uum = new UncheckedUserMetadata(m);
		assertThat("passing null map = empty metadata", uum.getMetadata(),
				is(mt));
		
		wum = null;
		uum = new UncheckedUserMetadata(wum);
		assertThat("passing null workspace meta = empty metadata",
				uum.getMetadata(),
				is(mt));
		
	}
	
	@Test
	public void getMetadata() throws Exception {
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
	
	@Test
	public void hashcodeCheck() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		assertThat("hashcode correct", uum.hashCode(), is(61684));
	}
	
	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void equalsCheck() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		UncheckedUserMetadata uum = new UncheckedUserMetadata(m);
		assertThat("equals same object", uum.equals(uum), is(true));
		assertThat("equals null", uum.equals(null), is(false));
		assertThat("equals diff object", uum.equals(m), is(false));
		
		Map<String, String> m1 = new HashMap<String, String>();
		m1.put("foo", "bar");
		UncheckedUserMetadata uum1 = new UncheckedUserMetadata(m1);
		assertThat("equals equal object", uum.equals(uum1), is(true));
		
		m1.put("baz", "whoo");
		uum1 = new UncheckedUserMetadata(m1);
		assertThat("equals unequal object", uum.equals(uum1), is(false));
	}
	
}
