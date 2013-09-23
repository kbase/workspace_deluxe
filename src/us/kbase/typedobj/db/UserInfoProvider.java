package us.kbase.typedobj.db;

public interface UserInfoProvider {
	public boolean isAdmin(String userId);
	public String getEmail(String userId);
}
