package us.kbase.workspace.database;

import static us.kbase.common.utils.StringUtils.checkString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;


public class WorkspaceIdentifier {
	
	private final static String WS_NAME_DELIMITER = ":";
	private final static Pattern INVALID_WS_NAMES = 
			Pattern.compile("[^\\w" + WS_NAME_DELIMITER + "]");
	public final static int MAX_NAME_LENGTH = 100;

	private final Long id;
	private final String wsname;
	
	public WorkspaceIdentifier(String wsname) {
		checkWorkspaceName(wsname, null);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(String wsname, WorkspaceUser user) {
		if (user == null) {
			throw new IllegalArgumentException("user cannot be null");
		}
		checkWorkspaceName(wsname, user);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("Workspace id must be > 0");
		}
		this.id = id;
		this.wsname = null;
	}
	
	public static void checkWorkspaceName(String name) {
		checkWorkspaceName(name, null);
	}

	public static void checkWorkspaceName(String name, WorkspaceUser user) {
		checkString(name, "Workspace name", MAX_NAME_LENGTH);
		int delimcount = StringUtils.countMatches(name, WS_NAME_DELIMITER);
		if (delimcount > 1) {
			throw new IllegalArgumentException(String.format(
					"Workspace name %s may only contain one %s delimiter",
					name, WS_NAME_DELIMITER));
		} else if (delimcount == 1) {
			String[] user_ws = name.split(WS_NAME_DELIMITER);
			if (user_ws.length < 2) {
				throw new IllegalArgumentException(String.format(
						"Workspace name missing from %s", name));
			}
			if (user_ws[0].length() == 0) {
				throw new IllegalArgumentException(String.format(
						"User name missing from %s", name));
			}
			if (user != null && !user_ws[0].equals(user.getUser())){
				throw new IllegalArgumentException(String.format(
						"Workspace name %s must only contain the user name %s prior to the %s delimiter",
						name, user, WS_NAME_DELIMITER));
			}
		}
		final Matcher m = INVALID_WS_NAMES.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in workspace name %s: %s", name, m.group()));
		}
	}
	
	public Long getId() {
		return id;
	}

	public String getName() {
		return wsname;
	}

	@Override
	public String toString() {
		return "WorkspaceIdentifier [id=" + id + ", name=" + wsname
				+ "]";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((wsname == null) ? 0 : wsname.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WorkspaceIdentifier other = (WorkspaceIdentifier) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (wsname == null) {
			if (other.wsname != null)
				return false;
		} else if (!wsname.equals(other.wsname))
			return false;
		return true;
	}

	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
		
	}
}
