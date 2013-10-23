package us.kbase.workspace.workspaces;

import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.Reference;

import com.fasterxml.jackson.databind.JsonNode;

public class ResolvedSaveObject {
	
	private final ObjectIDNoWSNoVer id;
	private final JsonNode data;
	private final AbsoluteTypeDefId type;
	private final Map<String, String> userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	private final Set<Reference> refs;
	private Set<Reference> provrefs;
	
	ResolvedSaveObject(final ObjectIDNoWSNoVer id,
			final JsonNode resolvedData, final AbsoluteTypeDefId type,
			final Map<String, String> userMeta, final Provenance provenance,
			final boolean hidden, final Set<Reference> refs,
			final Set<Reference> provenancerefs) {
		if (id == null || resolvedData == null || type == null || refs == null
				|| provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither id, data, refs, provenancerefs nor type may be null");
		}
		this.id = id;
		this.data = resolvedData;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.refs = refs;
		this.provrefs = provenancerefs;
	}
	
	ResolvedSaveObject(final JsonNode resolvedData,
			final AbsoluteTypeDefId type, final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden, 
			final Set<Reference> refs, final Set<Reference> provenancerefs) {
		if (resolvedData == null || type == null || refs == null
				|| provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither data, refs, provenancerefs nor type may be null");
		}
		this.id = null;
		this.data = resolvedData;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.refs = refs;
		this.provrefs = provenancerefs;
	}
	
	public ObjectIDNoWSNoVer getObjectIdentifier() {
		return id;
	}

	//mutable!
	public JsonNode getData() {
		return data;
	}

	public AbsoluteTypeDefId getType() {
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

	public Set<Reference> getRefs() {
		return refs;
	}

	public Set<Reference> getProvRefs() {
		return provrefs;
	}

	@Override
	public String toString() {
		return "ResolvedSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + ", refs=" + refs
				+ ", provrefs=" + provrefs + "]";
	}
}
