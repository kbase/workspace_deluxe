package us.kbase.workspace;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.workspace.database.Database;

public class Workspaces {
	
	private final static String WS_NAME_DELIMITER = ":";
	private final static Pattern VALID_WS_NAMES = 
			Pattern.compile("[^a-zA-Z0-9_" + WS_NAME_DELIMITER + "]");
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final Database db;
	
	public Workspaces(Database db) {
		if (db == null) {
			throw new NullPointerException("db");
		}
		this.db = db;
	}
	
	public Workspace createWorkspace(String user, String wsname,
			boolean globalread, String description) {
		if (user == null || wsname == null || description == null) {
			throw new NullPointerException("no args can be null");
		}
		//TODO dealing with users
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
