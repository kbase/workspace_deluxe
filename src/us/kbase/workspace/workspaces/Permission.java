package us.kbase.workspace.workspaces;

import java.util.HashMap;
import java.util.Map;

public enum Permission {
	NONE (0),
	READ (10),
	WRITE (20),
	ADMIN (30),
	OWNER (40);
	
	private static final Map<Integer, Permission> intToPerm = 
			new HashMap<Integer, Permission>();
	static {
		for (Permission p: Permission.values()) {
			intToPerm.put(p.getPermission(), p);
		}
	}
	
	private final int permission;
	
	private Permission(int permission) {
		this.permission = permission;
	}
	
	public int getPermission() {
		return permission;
	}
	
	public static Permission fromInt(int permission) {
		if (!intToPerm.containsKey(permission)) {
			throw new IllegalArgumentException("Invalid permission: " +
					permission);
		}
		return intToPerm.get(permission);
	}
	
	public static void main(String[] args) {
		System.out.println(Permission.NONE.equals(Permission.NONE));
		System.out.println(Permission.NONE.equals(Permission.READ));
		System.out.println(Permission.NONE.compareTo(Permission.READ));
		System.out.println(Permission.fromInt(2));
	}
}
