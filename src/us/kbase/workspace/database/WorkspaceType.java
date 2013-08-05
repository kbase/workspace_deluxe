package us.kbase.workspace.database;

//import java.net.URL;
//import java.util.List;
//import java.util.Map;

public class WorkspaceType {
	
	public String owner;
//	public List<Map<String, String>> users;
	public String module;
	public String type;
	public int version;
//	public Map<String, String> schema;
//	public boolean globalRead;
//	public URL landingURL;
	
	public WorkspaceType(String owner, String module, String type,
			int version) {
		if(owner == null || module == null || type == null) {
			throw new NullPointerException("args cannot be null");
		}
		if(version < 0) {
			throw new IllegalArgumentException("version cannot be <0");
		}
		this.owner = owner;
		this.module = module;
		this.type = type;
		this.version = version;
	}

	public String getOwner() {
		return owner;
	}

	public String getModule() {
		return module;
	}

	public String getType() {
		return type;
	}

	public int getVersion() {
		return version;
	}
}
