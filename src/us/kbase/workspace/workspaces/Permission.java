package us.kbase.workspace.workspaces;

public enum Permission {
	NONE (1),
	READ (2),
	WRITE (3),
	ADMIN (4);
	
	private final int permission;

	private Permission(int permission) {
		this.permission = permission;
	}
	
	public int getPermission() {
		return permission;
	}
}
