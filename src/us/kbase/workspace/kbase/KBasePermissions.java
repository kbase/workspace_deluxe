package us.kbase.workspace.kbase;

import java.util.HashMap;
import java.util.Map;

import us.kbase.workspace.workspaces.Permission;

public class KBasePermissions {
	private static final Map<Object, String> PERM_TO_API = new HashMap<Object, String>();
	private static final Map<String, Permission> API_TO_PERM = new HashMap<String, Permission>();
	public static final String PERM_NONE = "n";
	public static final String PERM_READ = "r";
	public static final String PERM_WRITE = "w";
	public static final String PERM_ADMIN = "a";
	static {
		API_TO_PERM.put(PERM_NONE, Permission.NONE);
		API_TO_PERM.put(PERM_READ, Permission.READ);
		API_TO_PERM.put(PERM_WRITE, Permission.WRITE);
		API_TO_PERM.put(PERM_ADMIN, Permission.ADMIN);
		for (String p: API_TO_PERM.keySet()) {
			PERM_TO_API.put(API_TO_PERM.get(p), p);
		}
		PERM_TO_API.put(false, PERM_NONE); // for globalread
		PERM_TO_API.put(true, PERM_READ); // for globalread
		PERM_TO_API.put(Permission.OWNER, PERM_ADMIN);
	}
	
	public static Permission translatePermission(String perm) {
		if (!API_TO_PERM.containsKey(perm)) {
			throw new IllegalArgumentException("No such permission: " + perm);
		}
		return API_TO_PERM.get(perm);
	}
	
	public static String translatePermission(Permission perm) {
		return PERM_TO_API.get(perm);
	}
	
	public static String translatePermission(boolean globalread) {
		return PERM_TO_API.get(globalread);
	}
}
