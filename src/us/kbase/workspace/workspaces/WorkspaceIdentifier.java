package us.kbase.workspace.workspaces;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class WorkspaceIdentifier {
	
	private final static String WS_NAME_DELIMITER = ":";
	private final static Pattern INVALID_WS_NAMES = 
			Pattern.compile("[^\\w" + WS_NAME_DELIMITER + "]");

	private final Integer id;
	private final String wsname;
	
	public WorkspaceIdentifier(String wsname) {
		checkWorkspaceName(wsname, null);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(String wsname, String user) {
		if (user == null || user.length() == 0) {
			throw new IllegalArgumentException("user cannot be null and must have at least one character");
		}
		checkWorkspaceName(wsname, user);
		this.id = null;
		this.wsname = wsname;
	}
	
	public WorkspaceIdentifier(int id) {
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		this.id = id;
		this.wsname = null;
	}
	
	public static void checkWorkspaceName(String name) {
		checkWorkspaceName(name, null);
	}

	public static void checkWorkspaceName(String name, String user) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("name cannot be null and must have at least one character");
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
			if (user != null && !user_ws[0].equals(user)){
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
	
	public Integer getId() {
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
	
	public static void main(String[] args) {
		System.out.println(new WorkspaceIdentifier("a:b", "a"));
	}

	public Object getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}
}
