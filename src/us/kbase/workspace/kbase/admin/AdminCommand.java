package us.kbase.workspace.kbase.admin;

import us.kbase.common.service.UObject;

public class AdminCommand {
	
	// TODO JAVADOC
	
	// this class is only instantiated from JSON.
	
	private String command;
	private String module;
	private String user;
	private UObject params;
	
	private AdminCommand() {}

	public String getCommand() {
		return command;
	}

	public String getModule() {
		return module;
	}

	public String getUser() {
		return user;
	}

	public UObject getParams() {
		return params;
	}
}
