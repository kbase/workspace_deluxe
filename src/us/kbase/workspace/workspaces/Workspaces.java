package us.kbase.workspace.workspaces;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.workspace.database.Database;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;

public class Workspaces {
	
	private final static String WS_NAME_DELIMITER = ":";
	private final static Pattern INVALID_WS_NAMES = 
			Pattern.compile("[^a-zA-Z0-9_" + WS_NAME_DELIMITER + "]");
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
	}
	
	public WorkspaceMetaData createWorkspace(String user, String wsname,
			boolean globalread, String description) {
		if (user == null || wsname == null || description == null) {
			throw new NullPointerException("no args can be null");
		}
		int delimcount = StringUtils.countMatches(wsname, WS_NAME_DELIMITER);
		if (delimcount > 1) {
			throw new IllegalArgumentException(String.format(
					"Workspace name %s may only contain one %s delimiter",
					wsname, WS_NAME_DELIMITER));
		} else if (delimcount == 1) {
			String[] user_ws = wsname.split(WS_NAME_DELIMITER);
			if (!user_ws[0].equals(user)){
				throw new IllegalArgumentException(String.format(
						"Workspace name %s may only contain the user name prior to the %s delimiter",
						wsname, WS_NAME_DELIMITER));
			}
		}
		checkWorkspaceName(wsname);
		if(description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
	}
	
	public void checkWorkspaceName(String wsname) {
		final Matcher m = INVALID_WS_NAMES.matcher(wsname);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in workspace name %s: %s", wsname, m.group()));
		}
	}
	
	public String getWorkspaceDescription(int workspaceid) throws NoSuchWorkspaceException {
		if (workspaceid < 1) {
			throw new IllegalArgumentException("Workspace ID must be > 0");
		}
		return db.getWorkspaceDescription(workspaceid);
		
	}
	
	public String getWorkspaceDescription(String wsname) throws NoSuchWorkspaceException {
		checkWorkspaceName(wsname);
		return db.getWorkspaceDescription(wsname);
	}
}
