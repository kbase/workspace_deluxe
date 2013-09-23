package us.kbase.typedobj.db;

public class UserInfoProviderForTests implements UserInfoProvider {
	@Override
	public boolean isAdmin(String userId) {
		return true;
	}
	
	@Override
	public String getEmail(String userId) {
		return null;
	}
}
