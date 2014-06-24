package us.kbase.workspace.lib;

import static us.kbase.workspace.database.Util.checkSize;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;

public class WorkspaceSaveObject {
	
	private static final int MAX_USER_META_SIZE = 16000;
	
	private final ObjectIDNoWSNoVer id;
	private final UObject data;
	private final TypeDefId type;
	private final Map<String, String> userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(final ObjectIDNoWSNoVer id, final Object data,
			final TypeDefId type, final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden) {
		if (id == null || data == null || type == null || provenance == null) {
			throw new IllegalArgumentException(
					"Neither id, provenance data nor type may be null");
		}
		this.id = id;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		checkSize(userMeta, "Metadata", MAX_USER_META_SIZE);
	}
	
	public WorkspaceSaveObject(final Object data, final TypeDefId type,
			final Map<String, String> userMeta,  final Provenance provenance,
			final boolean hidden) {
		if (data == null || type == null || provenance == null) {
			throw new IllegalArgumentException(
					"Neither data, provenance, nor type may be null");
		}
		this.id = null;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		checkSize(userMeta, "Metadata", MAX_USER_META_SIZE);
	}

	private UObject transformData(final Object data) {
		return data instanceof UObject ? (UObject)data : new UObject(data);
	}
	
	public ObjectIDNoWSNoVer getObjectIdentifier() {
		return id;
	}

	//mutable!
	public UObject getData() {
		return data;
	}

	public TypeDefId getType() {
		return type;
	}

	//mutable!
	public Map<String, String> getUserMeta() {
		return userMeta;
	}
	
	public static int getMaxUserMetaSize() {
		return MAX_USER_META_SIZE;
	}

	public Provenance getProvenance() {
		return provenance;
	}

	public boolean isHidden() {
		return hidden;
	}

	public ResolvedSaveObject resolve(final TypedObjectValidationReport rep,
			final Set<Reference> references,
			final List<Reference> provenancerefs) {
		if (id == null) {
			return new ResolvedSaveObject(this.userMeta, this.provenance,
					this.hidden, rep, references, provenancerefs);
		} else {
			return new ResolvedSaveObject(this.id, this.userMeta,
					this.provenance, this.hidden, rep, references,
					provenancerefs);
		}
	}
	
	@Override
	public String toString() {
		return "WorkspaceSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
