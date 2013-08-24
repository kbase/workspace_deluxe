package us.kbase.workspace.workspaces;

public enum Permission {
	NONE (0),
	READ (1),
	WRITE (2),
	ADMIN (3);
	
	private final int permission;

	private Permission(int permission) {
		this.permission = permission;
	}
	
	public int getPermission() {
		return permission;
	}
	
	public static void main(String[] args) {
		System.out.println(Permission.NONE.equals(Permission.NONE));
		System.out.println(Permission.NONE.equals(Permission.READ));
		System.out.println(Permission.NONE.compareTo(Permission.READ));
	}
}
