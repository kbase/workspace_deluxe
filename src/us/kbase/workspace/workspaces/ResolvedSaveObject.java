package us.kbase.workspace.workspaces;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
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
	private final TypedObjectValidationReport rep;
	private final Set<Reference> refs;
	private final List<Reference> provrefs;
	
	ResolvedSaveObject(final ObjectIDNoWSNoVer id,
			final JsonNode resolvedData, final AbsoluteTypeDefId type,
			final Map<String, String> userMeta, final Provenance provenance,
			final boolean hidden, final TypedObjectValidationReport rep,
			final Set<Reference> refs, final List<Reference> provenancerefs) {
		if (id == null || resolvedData == null || type == null || rep == null ||
				refs == null || provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither id, data, rep, refs, provenancerefs nor type may be null");
		}
		this.id = id;
		this.data = resolvedData;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.rep = rep;
		this.refs = refs;
		this.provrefs = provenancerefs;
	}
	
	ResolvedSaveObject(final JsonNode resolvedData,
			final AbsoluteTypeDefId type, final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden, 
			final TypedObjectValidationReport rep,
			final Set<Reference> refs, final List<Reference> provenancerefs) {
		if (resolvedData == null || type == null || rep == null || refs == null
				|| provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither data, rep, refs, provenancerefs nor type may be null");
		}
		this.id = null;
		this.data = resolvedData;
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.rep = rep;
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

	public TypedObjectValidationReport getRep() {
		return rep;
	}

	public Set<Reference> getRefs() {
		return refs;
	}

	public List<Reference> getProvRefs() {
		return provrefs;
	}

	@Override
	public String toString() {
		return "ResolvedSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + ", rep=" + rep
				+ ", refs=" + refs + ", provrefs=" + provrefs + "]";
	}
}
