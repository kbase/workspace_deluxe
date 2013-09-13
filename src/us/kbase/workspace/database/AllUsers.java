package us.kbase.workspace.database;

import java.util.HashSet;
import java.util.Set;


public class AllUsers implements User {
	
	private static final Set<Character> ALLOWED = new HashSet<Character>();
	static {
		ALLOWED.add('*');
		ALLOWED.add('@');
		ALLOWED.add('#');
		ALLOWED.add('%');
		ALLOWED.add('^');
		ALLOWED.add('&');
		ALLOWED.add('+');
		ALLOWED.add('-');
		ALLOWED.add('?');
	}
	
	private final String user;
	
	public AllUsers(char user) {
		if (!ALLOWED.contains(user)) {
			throw new IllegalArgumentException("Disallowed character: " + user);
		}
		this.user = Character.toString(user);
	}

	@Override
	public String getUser() {
		return user;
	}

	@Override
	public String toString() {
		return "AllUsers [user=" + user + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AllUsers)) {
			return false;
		}
		AllUsers other = (AllUsers) obj;
		if (user == null) {
			if (other.user != null) {
				return false;
			}
		} else if (!user.equals(other.user)) {
			return false;
		}
		return true;
	}

}
