package us.kbase.workspace.database;

import static us.kbase.common.utils.StringUtils.checkString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class WorkspaceIdentifier {
	
	public final static String WS_NAME_DELIMITER = ":";
	private final static Pattern WS_NAME_INVALID = 
			Pattern.compile("[^\\w" + WS_NAME_DELIMITER + "._-]");
	private final static Pattern WS_NAME_INTEGER = Pattern.compile("^-?\\d+$");
	public final static int MAX_NAME_LENGTH = 255;

	private final Long id;
	private final String wsname;
	
	public WorkspaceIdentifier(final String wsname) {
		checkWorkspaceName(wsname, null);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(final String wsname, final WorkspaceUser user) {
		if (user == null) {
			throw new IllegalArgumentException("user cannot be null");
		}
		checkWorkspaceName(wsname, user);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(final long id) {
		if (id < 1) {
			throw new IllegalArgumentException("Workspace id must be > 0");
		}
		this.id = id;
		this.wsname = null;
	}
	
	public static void checkWorkspaceName(final String name) {
		checkWorkspaceName(name, null);
	}

	public static void checkWorkspaceName(final String name,
			final WorkspaceUser user) {
		checkString(name, "Workspace name", MAX_NAME_LENGTH);
		Matcher m = WS_NAME_INVALID.matcher(name);
		String wsname = name;
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in workspace name %s: %s", name, m.group()));
		}
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
						name, user.getUser(), WS_NAME_DELIMITER));
			}
			wsname = user_ws[1];
		}
		m = WS_NAME_INTEGER.matcher(wsname);
		if (m.find()) {
			throw new IllegalArgumentException("Workspace names cannot be integers: " + name);
		}
	}
	
	//TODO TEST unit tests (for entire class?)
	public static String[] splitUser(final String wsName) {
		final String[] user_ws = wsName.split(WS_NAME_DELIMITER);
		if (user_ws.length == 2) {
			return user_ws;
		}
		if (user_ws.length == 1) {
			return new String[]{null, user_ws[0]};
		}
		throw new IllegalArgumentException("Illegal workspace name: " +
				wsName);
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
