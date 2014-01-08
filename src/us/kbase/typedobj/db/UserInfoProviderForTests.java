package us.kbase.typedobj.db;

public class UserInfoProviderForTests implements UserInfoProvider {
	private String adminUser;

	public UserInfoProviderForTests() {
	}
	
	public UserInfoProviderForTests(String admin) {
		this.adminUser = admin;
	}
	
	@Override
	public boolean isAdmin(String userId) {
		/*if (adminUser == null)
			return true;
		return adminUser.equals(userId);*/
		return false;
	}
	
	@Override
	public String getEmail(String userId) {
		return null;
	}
}
