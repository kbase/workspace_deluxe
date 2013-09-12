package us.kbase.workspace.workspaces;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkspaceUser implements User {
	
	private final static Pattern INVALID_USER_NAME = Pattern.compile("[^\\w-]");
	
	protected final String user;
	
	public WorkspaceUser(String user) {
		if (user == null || user.isEmpty()) {
			throw new IllegalArgumentException("User cannot be null or the empty string");
		}
		final Matcher m = INVALID_USER_NAME.matcher(user);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in user name %s: %s", user, m.group()));
		}
		this.user = user;
	}
	
	public String getUser() {
		return user;
	}

	@Override
	public String toString() {
		return "User [user=" + user + "]";
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
		if (!(obj instanceof WorkspaceUser)) {
			return false;
		}
		WorkspaceUser other = (WorkspaceUser) obj;
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
