package us.kbase.test.typedobj;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;

/* Note most tests are currently in TypeDefsTest.java. They should be cleaned up, completed,
 * and moved here.
 * Additionally, splitting both type classes into MD5 based types and standard types should
 * be explored, but that's quite a bit of work and requires tracing a lot of code paths to
 * see if they accept either kind of type.
 * 
 */

public class AbsoluteTypeDefIdTest {

	@Test
	public void getTypeStringMajorVersion() throws Exception {
		final AbsoluteTypeDefId t = new AbsoluteTypeDefId(new TypeDefName("Mod.Type"), 3, 1);
		assertThat("incorrect type string", t.getTypeStringMajorVersion(), is("Mod.Type-3"));
	}
}
