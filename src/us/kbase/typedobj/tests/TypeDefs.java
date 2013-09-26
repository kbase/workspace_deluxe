package us.kbase.typedobj.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;

public class TypeDefs {
	
	@Test
	public void type() throws Exception {
		checkTypeDefName(null, "bar", "Module cannot be null or the empty string");
		checkTypeDefName("foo", null, "Name cannot be null or the empty string");
		checkTypeDefName("", "bar", "Module cannot be null or the empty string");
		checkTypeDefName("foo", "", "Name cannot be null or the empty string");
		checkTypeDefName("fo-o", "bar", "Illegal character in type id fo-o: -");
		checkTypeDefName("foo", "ba/r", "Illegal character in type id ba/r: /");
		TypeDefName wst = new TypeDefName("foo", "bar");
		checkTypeId(null, null, null, "Type cannot be null");
		checkTypeId(null, 1, null, "Type cannot be null");
		checkTypeId(null, 1, 0, "Type cannot be null");
		checkTypeId(wst, -1, null, "Version numbers must be >= 0");
		checkTypeId(wst,  -1, 0, "Version numbers must be >= 0");
		checkTypeId(wst,  0, -1, "Version numbers must be >= 0");
		checkTypeId(null, "Moduletype cannot be null or the empty string");
		checkTypeId(null, null, "Moduletype cannot be null or the empty string");
		checkTypeId("", "Moduletype cannot be null or the empty string");
		checkTypeId("", null, "Moduletype cannot be null or the empty string");
		checkTypeId("foo", "Type foo could not be split into a module and name");
		checkTypeId("foo", null, "Type foo could not be split into a module and name");
		checkTypeId(".", "Type . could not be split into a module and name");
		checkTypeId(".", null, "Type . could not be split into a module and name");
		checkTypeId(".foo", "Module cannot be null or the empty string");
		checkTypeId(".foo", null, "Module cannot be null or the empty string");
		checkTypeId("foo.", "Type foo. could not be split into a module and name");
		checkTypeId("foo.", null, "Type foo. could not be split into a module and name");
		checkTypeId("foo.bar", "", "Typeversion cannot be an empty string");
		checkTypeId("foo.bar", "2.1.3", "Type version string 2.1.3 could not be parsed to a version");
		checkTypeId("foo.bar", "n", "Type version string n could not be parsed to a version");
		checkTypeId("foo.bar", "1.n", "Type version string 1.n could not be parsed to a version");
		checkTypeIdFromString(null, "Typestring cannot be null or the empty string");
		checkTypeIdFromString("", "Typestring cannot be null or the empty string");
		checkTypeIdFromString("foo.bar-2.1-3", "Could not parse typestring foo.bar-2.1-3 into module/type and version portions");
		checkTypeIdFromString("-2.1", "Moduletype cannot be null or the empty string");	
		checkTypeIdFromString("foo", "Type foo could not be split into a module and name");
		checkTypeIdFromString(".", "Type . could not be split into a module and name");
		checkTypeIdFromString(".foo", "Module cannot be null or the empty string");
		checkTypeIdFromString("foo.", "Type foo. could not be split into a module and name");
		checkTypeIdFromString("foo.bar-2.1.3", "Type version string 2.1.3 could not be parsed to a version");
		checkTypeIdFromString("foo.bar-n", "Type version string n could not be parsed to a version");
		checkTypeIdFromString("foo.bar-1.n", "Type version string 1.n could not be parsed to a version");
		
		assertTrue("absolute type", new TypeDefId(wst, 1, 1).isAbsolute());
		assertFalse("absolute type", new TypeDefId(wst, 1).isAbsolute());
		assertFalse("absolute type", new TypeDefId(wst).isAbsolute());
		assertThat("check typestring", new TypeDefId(wst, 1, 1).getTypeString(),
				is("foo.bar-1.1"));
		assertThat("check typestring", new TypeDefId(wst, 1).getTypeString(),
				is("foo.bar-1"));
		assertThat("check typestring", new TypeDefId(wst).getTypeString(),
				is("foo.bar"));
		assertNull("check verstring", new TypeDefId(wst).getVerString());
		assertThat("check verstring", new TypeDefId(wst, 1).getVerString(), is("1"));
		assertThat("check verstring", new TypeDefId(wst, 1, 1).getVerString(), is("1.1"));
	}
	
	@Test
	public void absType() throws Exception {
		TypeDefName wst = new TypeDefName("foo", "bar");
		checkAbsType(null, 1, 0, "Type cannot be null");
		checkAbsType(wst,  -1, 0, "Version numbers must be >= 0");
		checkAbsType(wst,  0, -1, "Version numbers must be >= 0");
		//TODO more abs type tests (from TypeId) assuming it sticks around after integration with Roman's code
	}
	
	private void checkTypeDefName(String module, String name, String exception) {
		try {
			new TypeDefName(module, name);
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkTypeId(TypeDefName t, Integer major, Integer minor, String exception) {
		try {
			if (minor == null) {
				if (major == null) {
					new TypeDefId(t);
				} else {
					new TypeDefId(t, major);
				}
			} else {
				new TypeDefId(t, major, minor);
			}
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkAbsType(TypeDefName t, Integer major, Integer minor, String exception) {
		try {
			new AbsoluteTypeDefId(t, major, minor);
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkAbsTypeFromType(TypeDefId type, Integer major, Integer minor, String exception) {
		try {
			if (minor == null) {
				if (major == null) {
					AbsoluteTypeDefId.fromTypeId(type);
				} else {
					AbsoluteTypeDefId.fromTypeId(type, major);
				}
			} else {
				AbsoluteTypeDefId.fromTypeId(type, major, minor);
			}
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkTypeId(String moduletype, String typever, String exception) {
		try {
			new TypeDefId(moduletype, typever);
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkTypeId(String moduletype, String exception) {
		try {
			new TypeDefId(moduletype);
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}
	
	private void checkTypeIdFromString(String type, String exception) {
		try {
			TypeDefId.fromTypeString(type);
			fail("Initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}

}
