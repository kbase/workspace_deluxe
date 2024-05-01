package us.kbase.test.typedobj;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypeProvider.ResolvedType;

public class TypeProviderTest {

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ResolvedType.class).usingGetClass().verify();
	}
	
	@Test
	public void construct() throws Exception {
		final ResolvedType rt = new ResolvedType(
				AbsoluteTypeDefId.fromAbsoluteTypeString("Foo.Bar-1.3"),
				"{\"schema\": \"goes_here\"}");
		
		assertThat("incorrect type def", rt.getType(),
				is(new AbsoluteTypeDefId(new TypeDefName("Foo.Bar"), 1, 3)));
		assertThat("incorrect json schema", rt.getJsonSchema(), is("{\"schema\": \"goes_here\"}"));
	}
	
	@Test
	public void constructFail() throws Exception {
		failConstruct(null, "{}", new NullPointerException("type"));
		failConstruct(AbsoluteTypeDefId.fromAbsoluteTypeString("Foo.Bar-1.3"), null,
				new NullPointerException("jsonSchema"));
		
	}
	
	private void failConstruct(
			final AbsoluteTypeDefId type,
			final String jsonSchema,
			final Exception expected) {
		try {
			new ResolvedType(type, jsonSchema);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
