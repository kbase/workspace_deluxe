package us.kbase.workspace.workspaces;

import java.util.Map;

public class WorkspaceObject {
	
	private final ObjectIdentifier id;
	private final WorkspaceIdentifier wsid;
	private final Map<String, Object> data;
	private final TypeId type;
	private final Map<String, Object> userMeta;
	private final Provenance provenance;
	
	public WorkspaceObject(ObjectIdentifier id, Map<String, Object> data, TypeId type,
			Map<String, Object> userMeta,  Provenance provenance) {
		if (id == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither id, data nor type may be null");
		}
		this.id = id;
		this.wsid = id.getWorkspaceIdentifier();
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
	}
	
	public WorkspaceObject(WorkspaceIdentifier wsid, Map<String, Object> data, TypeId type,
			Map<String, Object> userMeta,  Provenance provenance) {
		if (wsid == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither wsid, data nor type may be null");
		}
		this.id = null;
		this.wsid = wsid;
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
	}

	public ObjectIdentifier getObjectIdentifier() {
		return id;
	}

	public WorkspaceIdentifier getWsid() {
		return wsid;
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
