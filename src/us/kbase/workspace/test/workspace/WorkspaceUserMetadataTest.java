package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataKeyValueSizeException;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataSizeException;

public class WorkspaceUserMetadataTest {
	
	private static String TEXT900 = "";
	static {
		for (int i = 0; i < 90; i++) {
			TEXT900 += "suckmaster";
		}
	}
	
	@Test
	public void emptyMeta() throws Exception {
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata();
		Map<String, String> mt = new HashMap<String, String>();
		assertThat("meta is empty", wum.isEmpty(), is(true));
		assertThat("meta is size 0", wum.size(), is(0));
		assertThat("meta empty map", wum.getMetadata(), is(mt));
		
		wum = new WorkspaceUserMetadata(null);
		assertThat("meta is empty", wum.isEmpty(), is(true));
		assertThat("meta is size 0", wum.size(), is(0));
		assertThat("meta empty map", wum.getMetadata(), is(mt));
		
		wum = new WorkspaceUserMetadata(mt);
		assertThat("meta is empty", wum.isEmpty(), is(true));
		assertThat("meta is size 0", wum.size(), is(0));
		assertThat("meta empty map", wum.getMetadata(), is(mt));
	}
	
	@Test
	public void constructor() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		m.put("baz", "shazbat");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		assertThat("not empty", wum.isEmpty(), is(false));
		assertThat("size correct", wum.size(), is(2));
		assertThat("meta correct", wum.getMetadata(), is(m));
	}
	
	@Test
	public void addMetadata() throws Exception {
		// size overruns are checked in the big* test methods
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		m.put("baz", "whee");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		wum.addMetadata((Map<String, String>) null);
		assertThat("adding null doesn't change size", wum.size(), is(2));
		assertThat("adding null -> still empty", wum.isEmpty(), is(false));
		assertThat("adding null doesn't change meta", wum.getMetadata(),
				is(m));
		
		wum.addMetadata((WorkspaceUserMetadata) null);
		assertThat("adding null doesn't change size", wum.size(), is(2));
		assertThat("adding null -> still empty", wum.isEmpty(), is(false));
		assertThat("adding null doesn't change meta", wum.getMetadata(),
				is(m));
		
		Map<String, String> newm = new HashMap<String, String>();
		newm.put("foo", "bak");
		newm.put("suckmaster", "burstingfoam");
		
		Map<String, String> added = new HashMap<String, String>(m);
		added.putAll(newm);
		wum.addMetadata(newm);
		assertThat("correct size", wum.size(), is(3));
		assertThat("correct meta", wum.getMetadata(), is(added));
		
		wum = new WorkspaceUserMetadata(m);
		WorkspaceUserMetadata newwum = new WorkspaceUserMetadata(newm);
		wum.addMetadata(newwum);
		assertThat("correct size", wum.size(), is(3));
		assertThat("correct meta", wum.getMetadata(), is(added));
	}
	
	@Test
	public void unmodifiable() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		m.put("baz", "shazbat");
		Map<String, String> origm = new HashMap<String, String>(m);
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		m.put("suckmaster", "burstingfoam");
		assertThat("modifying input map doesn't affect meta",
				wum.getMetadata(), is(origm));
		
		Map<String, String> out = wum.getMetadata();
		try {
			out.put("whee", "whoo");
			fail("expected unsupported exception");
		} catch (UnsupportedOperationException uoe) {
			//pass
		}
	}
	
	@Test
	public void nullKeysAndValues() throws Exception {
		final Map<String, String> m = new HashMap<String, String>();
		m.put(null, "foo");
		m.put("bar", "baz");
		final MetadataException nullkey = new MetadataException(
				"Null values are not allowed for metadata keys");
		failCreateMeta(m, nullkey);
		final WorkspaceUserMetadata um = new WorkspaceUserMetadata();
		failAddMeta(um, m, nullkey);
		
		m.clear();
		m.put("foo", null);
		m.put("baz", "bar");
		final MetadataException nullvalue = new MetadataException(
				"Null value for metadata key foo");
		failCreateMeta(m, nullvalue);
		failAddMeta(um, m, nullvalue);
		
	}
	
	@Test
	public void bigKeyValue() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put(TEXT900.substring(1), "a");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m); // should work
		wum.addMetadata(m); //should work
		m.put(TEXT900, "b");
		MetadataKeyValueSizeException e = new MetadataKeyValueSizeException(
				"Total size of metadata key + value exceeds maximum of 900B for key "
						+ TEXT900);
		failCreateMeta(m, e);
		failAddMeta(wum, m, e);
		
		m.clear();
		m.put("a", TEXT900.substring(1));
		wum = new WorkspaceUserMetadata(m); // should work
		wum.addMetadata(m); //should work
		m.put("a", TEXT900);
		e = new MetadataKeyValueSizeException(
				"Total size of metadata key + value exceeds maximum of 900B for key a");
		failCreateMeta(m, e);
		failAddMeta(wum, m, e);
	}
	
	@Test
	public void bigMetaAndCheckMetadataSize() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		for (int i = 0; i < 17; i++) {
			m.put(i < 10 ? "0" + i : "" + i, TEXT900.substring(2));
		}
		m.put("bar", TEXT900.substring(312));
		new WorkspaceUserMetadata(m); //should work
		WorkspaceUserMetadata.checkMetadataSize(m);
		m.put("bar", TEXT900.substring(311));
		final MetadataSizeException e = new MetadataSizeException(
				"Metadata exceeds maximum of 16000B");
		failCreateMeta(m, e);
		try {
			WorkspaceUserMetadata.checkMetadataSize(m);
		} catch (MetadataSizeException mse) {
			assertThat("correct message", mse.getLocalizedMessage(),
					is("Metadata exceeds maximum of 16000B"));
		}
		
		m.remove("bar");
		Map<String, String> newm = new HashMap<String, String>();
		newm.put("bar", TEXT900.substring(312));
		new WorkspaceUserMetadata(m).addMetadata(newm); //should work
		newm.put("bar", TEXT900.substring(311));
		failAddMeta(new WorkspaceUserMetadata(m), newm, e);
	}
	
	private void failCreateMeta(Map<String, String> meta, Exception e) {
		try {
			new WorkspaceUserMetadata(meta);
			fail("Created bad metadata");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	private void failAddMeta(WorkspaceUserMetadata wum,
			Map<String, String> meta, Exception e) {
		try {
			wum.addMetadata(meta);;
			fail("Added bad metadata");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, e);
		}
	}
	
	@Test
	public void string() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		WorkspaceUserMetadata uum = new WorkspaceUserMetadata(m);
		assertThat("correct toString()", uum.toString(),
				is("WorkspaceUserMetadata [metadata={foo=bar}]"));
	}
	
	@Test
	public void hashcodeCheck() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("whee", "shazbat");
		WorkspaceUserMetadata wum = new WorkspaceUserMetadata(m);
		assertThat("correct hashcode", wum.hashCode(), is(2051265365));
	}
	
	@Test
	public void equalsCheck() throws Exception {
		Map<String, String> m = new HashMap<String, String>();
		m.put("foo", "bar");
		WorkspaceUserMetadata uum = new WorkspaceUserMetadata(m);
		assertThat("equals same object", uum.equals(uum), is(true));
		assertThat("equals null", uum.equals(null), is(false));
		assertThat("equals diff object", uum.equals(m), is(false));
		
		Map<String, String> m1 = new HashMap<String, String>();
		m1.put("foo", "bar");
		WorkspaceUserMetadata uum1 = new WorkspaceUserMetadata(m1);
		assertThat("equals equal object", uum.equals(uum1), is(true));
		
		m1.put("baz", "whoo");
		uum1 = new WorkspaceUserMetadata(m1);
		assertThat("equals unequal object", uum.equals(uum1), is(false));
	}
}
