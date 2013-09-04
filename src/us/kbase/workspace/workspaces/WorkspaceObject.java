package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

import java.util.Map;

public class WorkspaceObject {
	
	private final String name;
	private final Map<String, Object> data;
	private final TypeId type;
	private final Map<String, Object> userMeta;
	private final Provenance provenance;
	
	public WorkspaceObject(String name, Map<String, Object> data, TypeId type,
			Map<String, Object> userMeta,  Provenance provenance) {
		checkString(name, "name");
		if (data == null || type == null) {
			throw new IllegalArgumentException("Neither data nor type may be null");
		}
		this.name = name;
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
	}

	public String getName() {
		return name;
	}

	//mutable!
	public Map<String, Object> getData() {
		return data;
	}

	public TypeId getType() {
		return type;
	}

	//mutable!
	public Map<String, Object> getUserMeta() {
		return userMeta;
	}

	public Provenance getProvenance() {
		return provenance;
	}
}
