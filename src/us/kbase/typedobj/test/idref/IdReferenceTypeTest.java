package us.kbase.typedobj.test.idref;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.idref.IdReferenceType;

public class IdReferenceTypeTest {
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(IdReferenceType.class).usingGetClass().verify();
	}

	@Test
	public void construct() throws Exception {
		final IdReferenceType t = new IdReferenceType("a");
		
		assertThat("incorrect type", t.getType(), is("a"));
		assertThat("incorrect toString", t.toString(), is("IDReferenceType [type=a]"));
	}
	
	@Test
	public void constructLong() throws Exception {
		
		final IdReferenceType t = new IdReferenceType(
				String.join("", Collections.nCopies(10000, "s")));
		
		assertThat("incorrect type", t.getType(),
				is(String.join("", Collections.nCopies(10000, "s"))));
		assertThat("incorrect toString", t.toString(), is(String.format(
				"IDReferenceType [type=%s]", String.join("", Collections.nCopies(10000, "s")))));
	}
	
	@Test
	public void failConstruct() throws Exception {
		failConstruct(null, new IllegalArgumentException(
				"type cannot be null or whitespace only"));
		failConstruct("    \t    \n    ", new IllegalArgumentException(
				"type cannot be null or whitespace only"));
	}
	
	private void failConstruct(final String type, final Exception expected) {
		try {
			new IdReferenceType(type);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void compareTo() throws Exception {
		assertThat("incorrect compare",
				new IdReferenceType("a").compareTo(new IdReferenceType("a")), is(0));
		assertThat("incorrect compare",
				new IdReferenceType("a").compareTo(new IdReferenceType("b")), is(-1));
		assertThat("incorrect compare",
				new IdReferenceType("b").compareTo(new IdReferenceType("a")), is(1));
	}
	
	@Test
	public void compareToFailNull() throws Exception {
		
		try {
			new IdReferenceType("a").compareTo(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("other"));
		}
		
	}

}
