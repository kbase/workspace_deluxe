package us.kbase.test.typedobj;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.LocalTypeProvider;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.core.TypeProvider.ResolvedType;


public class LocalTypeProviderTest {

	@Test
	public void getTypeJsonSchema() throws Exception {
		final TypeDefinitionDB tdb = mock(TypeDefinitionDB.class);
		
		final LocalTypeProvider ltp = new LocalTypeProvider(tdb);
		
		when(tdb.resolveTypeDefId(TypeDefId.fromTypeString("Baz.Bat-6")))
				.thenReturn(AbsoluteTypeDefId.fromAbsoluteTypeString("Baz.Bat-6.12"));
		when(tdb.getJsonSchemaDocument(AbsoluteTypeDefId.fromAbsoluteTypeString("Baz.Bat-6.12")))
				.thenReturn("{\"foo\":\"bar\"}");
		
		assertThat("incorrect result", ltp.getTypeJsonSchema(new TypeDefId("Baz.Bat", "6")),
				is(new ResolvedType(
						new AbsoluteTypeDefId(new TypeDefName("Baz.Bat"), 6, 12),
						"{\"foo\":\"bar\"}")));
	}
	
	@Test
	public void constructFail() throws Exception {
		try {
			new LocalTypeProvider(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("typeDB"));
		}
	}
	
	@Test
	public void getTypeJsonSchemaFail() throws Exception {
		final TypeDefinitionDB tdb = mock(TypeDefinitionDB.class);
		final LocalTypeProvider ltp = new LocalTypeProvider(tdb);
		try {
			ltp.getTypeJsonSchema(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("type"));
		}
	}
	
}
