package us.kbase.test.typedobj;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.test.common.TestCommon.assertExceptionCorrect;

import org.junit.Test;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;

/* Note most tests are currently in TypeDefsTest.java. They should be cleaned up, completed,
 * and moved here.
 * Additionally, splitting both type classes into MD5 based types and standard types should
 * be explored, but that's quite a bit of work and requires tracing a lot of code paths to
 * see if they accept either kind of type.
 * 
 */

public class TypeDefIdTest {

	@Test
	public void getTypeStringMajorVersion() throws Exception {
		TypeDefId t = new TypeDefId(new TypeDefName("Mod.Type"), 3, 1);
		assertThat("incorrect type string", t.getTypeStringMajorVersion(), is("Mod.Type-3"));
		
		t = new TypeDefId(new TypeDefName("Mod.Type"), 3);
		assertThat("incorrect type string", t.getTypeStringMajorVersion(), is("Mod.Type-3"));
	}
	
	@Test
	public void getTypeStringMajorVersionFail() throws Exception {
		final TypeDefId t = new TypeDefId(new TypeDefName("Mod.Type"));
		try {
			t.getTypeStringMajorVersion();
			fail("expected exception");
		} catch (Exception got) {
			assertExceptionCorrect(got, new IllegalStateException(
					"The major type version is not present"));
		}
		
	}
}
