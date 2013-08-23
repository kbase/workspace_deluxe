package us.kbase.workspace.kbase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.kbase.workspace.workspaces.WorkspaceIdentifier;

public class KBWorkspaceIDFactory {
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws.(\\d+)");
	
	
	public static WorkspaceIdentifier create(String wsname) {
		Matcher m = KB_WS_ID.matcher(wsname);
		if (m.find()) {
			return new WorkspaceIdentifier(new Integer(m.group(1)));
		}
		return new WorkspaceIdentifier(wsname);
	}
	
	public static WorkspaceIdentifier create(int id) {
		return new WorkspaceIdentifier(id);
		
	}

	public static void main(String[] args) {
		System.out.println(create(new Integer(0)));

	}

}
