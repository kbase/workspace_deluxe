package us.kbase.typedobj.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;

public class TypeDefsTest {
	
	private static String str255 = "";
	static {
		for (int i = 0; i < 255; i++) {
			str255 += "a";
		}
	}
	
	@Test
	public void type() throws Exception {
		checkTypeDefName(null, "bar", "Module name cannot be null or the empty string");
		checkTypeDefName("foo", null, "Type name cannot be null or the empty string");
		checkTypeDefName("", "bar", "Module name cannot be null or the empty string");
		checkTypeDefName("foo", "", "Type name cannot be null or the empty string");
		checkTypeDefName("fo-o", "bar", "Illegal character in Module name fo-o: -");
		checkTypeDefName("foo", "ba/r", "Illegal character in Type name ba/r: /");
		checkTypeDefName(str255 + "a", "bar", "Module name size is > 255 bytes");
		checkTypeDefName("foo", str255 + "a", "Type name size is > 255 bytes");
		TypeDefName wst = new TypeDefName("foo", "bar");
		new TypeDefName(str255, str255); //should work
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
		checkTypeId(".foo", "Module name cannot be null or the empty string");
		checkTypeId(".foo", null, "Module name cannot be null or the empty string");
		checkTypeId("foo.", "Type foo. could not be split into a module and name");
		checkTypeId("foo.", null, "Type foo. could not be split into a module and name");
		checkTypeId(str255 + "a.foo", "Module name size is > 255 bytes");
		checkTypeId("a.a" + str255, "Type name size is > 255 bytes");
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
		checkTypeIdFromString(".foo", "Module name cannot be null or the empty string");
		checkTypeIdFromString("foo.", "Type foo. could not be split into a module and name");
		checkTypeIdFromString(str255 + "a.bar-2.1.3", "Module name size is > 255 bytes");
		checkTypeIdFromString("foo.a" + str255 + "-2.1.3", "Type name size is > 255 bytes");
		checkTypeIdFromString("foo.bar-2.1.3", "Type version string 2.1.3 could not be parsed to a version");
		checkTypeIdFromString("foo.bar-n", "Type version string n could not be parsed to a version");
		checkTypeIdFromString("foo.bar-1.n", "Type version string 1.n could not be parsed to a version");
		checkTypeIdFromString("foo.bar-1111111111111111111111111111111g",
				"Type version string could not be parsed to a version: 1111111111111111111111111111111g is not a valid MD5 string");
		
		TypeDefName foobar = new TypeDefName("foo.bar");
		try {
			new TypeDefId(foobar, null);
			fail("initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is("md5 cannot be null"));
		}
		assertThat("TypeDefName constructor of Mod.type parsed module name",foobar.getModule(),is("foo"));
		assertThat("TypeDefName constructor of Mod.type parsed type name",foobar.getName(),is("bar"));
		checkTypeDefNameFromString("foobar", "Illegal fullname of a typed object: foobar");
		checkTypeDefNameFromString("f.o.o.b.a.r", "Illegal fullname of a typed object: f.o.o.b.a.r");
		checkTypeDefNameFromString("f .r", "Illegal character in Module name f :  ");
		checkTypeDefNameFromString("foobar.", "Illegal fullname of a typed object: foobar.");
		checkTypeDefNameFromString(".foobar", "Module name cannot be null or the empty string");
		
		MD5 m = new MD5("a216f0dba216f0dba216f0dba216f0db");
		assertTrue("absolute type", new TypeDefId(wst, 1, 1).isAbsolute());
		assertTrue("absolute type", new TypeDefId(wst, m).isAbsolute());
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
		
		assertThat("get correct md5", new TypeDefId(foobar, m).getMd5(), is(m));
		assertThat("get correct md5", new TypeDefId("foo.bar", m.getMD5()).getMd5(), is(m));
		assertThat("get correct md5", TypeDefId.fromTypeString(
				"foo.bar-" + m.getMD5()).getMd5(), is(m));
		assertThat("get correct md5", TypeDefId.fromTypeString(
				"foo.bar-" + m.getMD5()).getVerString(), is(m.getMD5()));
		assertThat("get correct typestring", TypeDefId.fromTypeString(
				"foo.bar-" + m.getMD5()).getTypeString(), is("foo.bar-" + m.getMD5()));
		
	}
	
	@Test
	public void checkTypeName() throws Exception {
		checkTypeNameHlpr(null, "thingy", "thingy cannot be null or the empty string");
		checkTypeNameHlpr("", "thingy", "thingy cannot be null or the empty string");
		checkTypeNameHlpr("a-b", "thingy", "Illegal character in thingy a-b: -");
		checkTypeNameHlpr(str255 + "a", "thingy", "thingy size is > 255 bytes");
		TypeDefName.checkTypeName(str255, "whoo"); //should work
	}
	
	private void checkTypeNameHlpr(String name, String dataName, String exp) {
		try {
			TypeDefName.checkTypeName(name, dataName);
			fail("should've thrown exception");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception string", iae.getLocalizedMessage(),
					is(exp));
		}
	}
	
	@Test
	public void absType() throws Exception {
		TypeDefName wst = new TypeDefName("foo", "bar");
		MD5 m = new MD5("a216f0dba216f0dba216f0dba216f0db");
		checkAbsType(null, 1, 0, "Type cannot be null");
		checkAbsType(wst,  -1, 0, "Version numbers must be >= 0");
		checkAbsType(wst,  0, -1, "Version numbers must be >= 0");
		checkAbsTypeFromType(null, null, null, "Type cannot be null");
		checkAbsTypeFromType(null, null, 1, "Type cannot be null");
		checkAbsTypeFromType(null, 1, 1, "Type cannot be null");
		checkAbsTypeFromType(new TypeDefId(wst), null, null, "Type must be absolute");
		checkAbsTypeFromType(new TypeDefId(wst, 1), null, null, "Type must be absolute");
		checkAbsTypeFromType(new TypeDefId(wst), null, 1, "Incoming type major version cannot be null");
		assertTrue("absolute type", new AbsoluteTypeDefId(wst, 1, 1).isAbsolute());
		assertTrue("absolute type", new AbsoluteTypeDefId(wst, m).isAbsolute());
		List<AbsoluteTypeDefId> ids = Arrays.asList(new AbsoluteTypeDefId(wst, 1, 1),
				AbsoluteTypeDefId.fromTypeId(new TypeDefId(wst, 1, 1)),
				AbsoluteTypeDefId.fromTypeId(new TypeDefId(wst, 1), 1),
				AbsoluteTypeDefId.fromTypeId(new TypeDefId(wst), 1, 1));
		for (AbsoluteTypeDefId id: ids) {
			assertThat("check typestring",id.getTypeString(),
					is("foo.bar-1.1"));
		}
		assertThat("check verstring", new AbsoluteTypeDefId(wst, 1, 1).getVerString(),
				is("1.1"));
		
		try {
			new AbsoluteTypeDefId(wst, null);
			fail("initialized invalid type");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is("md5 cannot be null"));
		}
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
			if (minor != null) {
				if (major != null) {
					AbsoluteTypeDefId.fromTypeId(type, major, minor);
				} else {
					AbsoluteTypeDefId.fromTypeId(type, minor);
				}
			} else {
				AbsoluteTypeDefId.fromTypeId(type);
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
	
	private void checkTypeDefNameFromString(String invalidTypeNameString, String exception) {
		try {
			new TypeDefName(invalidTypeNameString);
			fail("Initialized invalid type def name");
		} catch (IllegalArgumentException e) {
			assertThat("correct exception string", e.getLocalizedMessage(), is(exception));
		}
	}

}
