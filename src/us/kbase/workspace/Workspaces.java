package us.kbase.workspace;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.kbase.workspace.database.Database;

public class Workspaces {
	
	private final static Pattern VALID_WS_NAMES = 
			Pattern.compile("[^a-zA-Z0-9_]");
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final Database db;
	
	public Workspaces(Database db) {
		this.db = db;
	}
	
	public Workspace createWorkspace(String user, String wsname,
			boolean globalread, String description) {
		//TODO dealing with users
		final Matcher m = VALID_WS_NAMES.matcher(wsname);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in workspace name %s: %s", wsname, m.group()));
		}
		if(description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		db.createWorkspace(user, wsname, globalread, description);
		return null; //TODO return ws
	}

}
