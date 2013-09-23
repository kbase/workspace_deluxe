package us.kbase.workspace.workspaces;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.typedobj.core.TypeId;
import us.kbase.workspace.database.WorkspaceObjectID;

public class WorkspaceSaveObject {
	
	private final WorkspaceObjectID id;
	private final JsonNode data;
	private final TypeId type;
	private final Map<String, String> userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(WorkspaceObjectID id, JsonNode data, TypeId type,
			Map<String, String> userMeta,  Provenance provenance, boolean hidden) {
		if (id == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither id, data nor type may be null");
		}
		this.id = id;
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}
	
	public WorkspaceSaveObject(JsonNode data, TypeId type,
			Map<String, String> userMeta,  Provenance provenance,
			boolean hidden) {
		if (data == null || type == null) {
			throw new IllegalArgumentException("Neither data nor type may be null");
		}
		this.id = null;
		this.data = data;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}

	public WorkspaceObjectID getObjectIdentifier() {
		return id;
	}

	//mutable!
	public JsonNode getData() {
		return data;
	}

	public TypeId getType() {
		return type;
	}

	//mutable!
	public Map<String, String> getUserMeta() {
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
		return "WorkspaceSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
