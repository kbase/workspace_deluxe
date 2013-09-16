package us.kbase.workspace.util;

public class Util {
	
	public static void checkString(String s, String sname) {
		if (s == null || s.length() == 0) {
			throw new IllegalArgumentException(sname + " cannot be null or the empty string");
		}
	}
	
	public static void xorNameId(final String name, final Integer id, 
			final String type) {
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of %s name (was: %s) or id (was: %s)",
					type, name, id));
		}
	}

}
