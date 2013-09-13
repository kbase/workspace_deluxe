package us.kbase.workspace.workspaces;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.WorkspaceIdentifier;

public class WorkspaceSaveObject {
	
	private final ObjectIdentifier id;
	private final WorkspaceIdentifier wsid;
	private final Object data;
	private final TypeId type;
	private final Object userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(ObjectIdentifier id, Object data, TypeId type,
			Object userMeta,  Provenance provenance, boolean hidden) {
		if (id == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither id, data nor type may be null");
		}
		this.id = id;
		this.wsid = id.getWorkspaceIdentifier();
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}
	
	public WorkspaceSaveObject(WorkspaceIdentifier wsid, Object data, TypeId type,
			Object userMeta,  Provenance provenance, boolean hidden) {
		if (wsid == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither wsid, data nor type may be null");
		}
		this.id = null;
		this.wsid = wsid;
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}

	public ObjectIdentifier getObjectIdentifier() {
		return id;
	}

	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsid;
	}

	//mutable!
	public Object getData() {
		return data;
	}

	public TypeId getType() {
		return type;
	}

	//mutable!
	public Object getUserMeta() {
		return userMeta;
	}

	public Provenance getProvenance() {
		return provenance;
	}

	public boolean isHidden() {
		return hidden;
	}

	@Override
	public String toString() {
		return "WorkspaceObject [id=" + id + ", wsid=" + wsid + ", data="
				+ data + ", type=" + type + ", userMeta=" + userMeta
				+ ", provenance=" + provenance + ", hidden=" + hidden + "]";
	}
}
