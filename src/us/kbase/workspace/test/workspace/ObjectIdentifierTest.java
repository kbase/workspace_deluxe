package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.common.base.Optional;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;

public class ObjectIdentifierTest {

	//TODO TEST test the other object id classes
	
	private final static String LONG256;
	static {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 25; i++) {
			sb.append("abcdefghij");
		}
		sb.append("123456");
		LONG256 = sb.toString();
	}
	
	@Test
	public void equalsNoWSnNoVer() {
		EqualsVerifier.forClass(ObjectIDNoWSNoVer.class).usingGetClass().verify();
	}
	
	@Test
	public void constructNoWSNoVerWithName() {
		final ObjectIDNoWSNoVer id = new ObjectIDNoWSNoVer("foo");
		assertThat("incorrect name", id.getName(), is(Optional.of("foo")));
		assertThat("incorrect id", id.getId(), is(Optional.absent()));
		assertThat("incorrect ident string", id.getIdentifierString(), is("foo"));
		assertThat("incorrect toString", id.toString(),
				is("ObjectIDNoWSNoVer [name=foo, id=null]"));
	}
	
	@Test
	public void constructNoWSNoVerWithNameFail() {
		// most of the tests for bad names are in the tests for the static checkObjectName method
		try {
			new ObjectIDNoWSNoVer("2400000000000000000000000000000000000000");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e, new IllegalArgumentException(
					"Object names cannot be integers: 2400000000000000000000000000000000000000"));
		}
		
	}
	
	@Test
	public void constructNoWSNoVerWithID() {
		final ObjectIDNoWSNoVer id = new ObjectIDNoWSNoVer(6);
		assertThat("incorrect name", id.getName(), is(Optional.absent()));
		assertThat("incorrect id", id.getId(), is(Optional.of(6L)));
		assertThat("incorrect ident string", id.getIdentifierString(), is("6"));
		assertThat("incorrect toString", id.toString(),
				is("ObjectIDNoWSNoVer [name=null, id=6]"));
	}
	
	@Test
	public void constructNoWSNoVerWithIDFail() {
		try {
			new ObjectIDNoWSNoVer(0);
			fail("expected exception");
		} catch (Exception e) {
			TestCommon.assertExceptionCorrect(e,
					new IllegalArgumentException("Object id must be > 0"));
		}
	}
	
	@Test
	public void create() {
		checkCreate(null, "foo", new ObjectIDNoWSNoVer("foo"));
		checkCreate(6L, null, new ObjectIDNoWSNoVer(6));
	}
	
	private void checkCreate(
			final Long id,
			final String name,
			final ObjectIDNoWSNoVer expected) {
		assertThat("incorrect create result", ObjectIDNoWSNoVer.create(name, id), is(expected));
	}
	
	@Test
	public void createFail() {
		failCreate(null, null, new IllegalArgumentException(
				"Must provide one and only one of object name (was: null) or id (was: null)"));
		failCreate(6L, "foo", new IllegalArgumentException(
				"Must provide one and only one of object name (was: foo) or id (was: 6)"));
	}
	
	private void failCreate(
			final Long id,
			final String name,
			final Exception e) {
		try {
			ObjectIDNoWSNoVer.create(name, id);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void checkObjectName() {
		// expect no exception
		ObjectIDNoWSNoVer.checkObjectName("|f.o-o_bA2r");
	}
	
	@Test
	public void checkObjectNameFail() {
		failCheckObjectName(null, "Object name cannot be null or the empty string");
		//TODO TEST add white space when common code trim()s the name
		failCheckObjectName("", "Object name cannot be null or the empty string");
		failCheckObjectName(LONG256, "Object name exceeds the maximum length of 255");
		failCheckObjectName("foo*bar", "Illegal character in object name foo*bar: *");
		failCheckObjectName("&foobar", "Illegal character in object name &foobar: &");
		failCheckObjectName("2400000000000000000000000000000000000000",
				"Object names cannot be integers: 2400000000000000000000000000000000000000");
		failCheckObjectName("-2400000000000000000000000000000000000000",
				"Object names cannot be integers: -2400000000000000000000000000000000000000");
	}
	
	private void failCheckObjectName(final String name, final String exception) {
		try {
			ObjectIDNoWSNoVer.checkObjectName(name);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(exception));
		}
	}
	
}
