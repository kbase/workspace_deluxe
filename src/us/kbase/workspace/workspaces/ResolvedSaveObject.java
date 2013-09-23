package us.kbase.workspace.workspaces;

import java.util.Map;

import us.kbase.typedobj.core.AbsoluteTypeId;
import us.kbase.workspace.database.WorkspaceObjectID;

import com.fasterxml.jackson.databind.JsonNode;

public class ResolvedSaveObject {
	
	private final WorkspaceObjectID id;
	private final JsonNode data;
	private final AbsoluteTypeId type;
	private final Map<String, String> userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	ResolvedSaveObject(final WorkspaceObjectID id,
			final JsonNode resolvedData, final AbsoluteTypeId type,
			final Map<String, String> userMeta, final Provenance provenance,
			final boolean hidden) {
		if (id == null || resolvedData == null || type == null) {
			throw new IllegalArgumentException("Neither id, data nor type may be null");
		}
		this.id = id;
		this.data = resolvedData;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}
	
	ResolvedSaveObject(final JsonNode resolvedData,
			final AbsoluteTypeId type, final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden) {
		if (resolvedData == null || type == null) {
			throw new IllegalArgumentException("Neither data nor type may be null");
		}
		this.id = null;
		this.data = resolvedData;
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

	public AbsoluteTypeId getType() {
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
		return "ResolvedSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
