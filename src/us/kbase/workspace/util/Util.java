package us.kbase.workspace.util;

public class Util {
	
	public static void checkString(String s, String sname) {
		if (s == null || s.length() == 0) {
			throw new IllegalArgumentException(sname + " cannot be null or the empty string");
		}
	}

}
