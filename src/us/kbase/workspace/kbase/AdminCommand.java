package us.kbase.workspace.kbase;

import us.kbase.common.service.UObject;

public class AdminCommand {
	
	//TODO 1 check with Roman that this'll do what I expect.
	
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
