package us.kbase.workspace.workspaces;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.Reference;

public class WorkspaceSaveObject {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_USER_META_SIZE = 16000;
	
	private final ObjectIDNoWSNoVer id;
	private final JsonNode data;
	private final TypeDefId type;
	private final Map<String, String> userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(final ObjectIDNoWSNoVer id, final Object data,
			final TypeDefId type, final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden) {
		if (id == null || data == null || type == null) {
			throw new IllegalArgumentException("Neither id, data nor type may be null");
		}
		this.id = id;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		checkMeta(userMeta);
	}
	
	public WorkspaceSaveObject(final Object data, final TypeDefId type,
			final Map<String, String> userMeta,  final Provenance provenance,
			final boolean hidden) {
		if (data == null || type == null) {
			throw new IllegalArgumentException("Neither data nor type may be null");
		}
		this.id = null;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		checkMeta(userMeta);
	}

	private JsonNode transformData(final Object data) {
		final JsonNode retdata;
		if (!(data instanceof JsonNode)) {
			try {
				retdata = MAPPER.valueToTree(data);
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException("Cannot serialize data", iae);
			}
		} else {
			retdata = (JsonNode) data;
		}
		return retdata;
	}
	
	private final static String META_ERR = String.format(
			"Metadata is > %s bytes", MAX_USER_META_SIZE);
	
	private void checkMeta(final Map<String, String> meta) {
		if (meta != null) {
			final String jsonmeta;
			try {
				jsonmeta = MAPPER.writeValueAsString(meta);
			} catch (JsonProcessingException jpe) {
				throw new IllegalArgumentException(
						"Unable to serialize metadata", jpe);
			}
			if (jsonmeta.length() > MAX_USER_META_SIZE) {
				throw new IllegalArgumentException(META_ERR);
			}
		}
	}

	public ObjectIDNoWSNoVer getObjectIdentifier() {
		return id;
	}

	//mutable!
	public JsonNode getData() {
		return data;
	}

	public TypeDefId getType() {
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

	public ResolvedSaveObject resolve(final AbsoluteTypeDefId type,
			final JsonNode resolvedData, final TypedObjectValidationReport rep,
			final Set<Reference> references,
			final Set<Reference> provenancerefs) {
		if (id == null) {
			return new ResolvedSaveObject(resolvedData, type, this.userMeta,
					this.provenance, this.hidden, rep, references, provenancerefs);
		} else {
			return new ResolvedSaveObject(this.id, resolvedData, type,
					this.userMeta, this.provenance, this.hidden, rep,
					references, provenancerefs);
		}
	}
	
	@Override
	public String toString() {
		return "WorkspaceSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
