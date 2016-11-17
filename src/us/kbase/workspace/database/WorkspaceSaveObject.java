package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;

public class WorkspaceSaveObject {
	
	private final ObjectIDNoWSNoVer id;
	private final UObject data;
	private final TypeDefId type;
	private final WorkspaceUserMetadata userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(final ObjectIDNoWSNoVer id, final Object data,
			final TypeDefId type, final WorkspaceUserMetadata userMeta,
			final Provenance provenance, final boolean hidden) {
		if (id == null || data == null || type == null || provenance == null) {
			throw new IllegalArgumentException(
					"Neither id, provenance data, nor type may be null");
		}
		this.id = id;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta == null ?
				new WorkspaceUserMetadata() : userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
	}
	
	public WorkspaceSaveObject(final Object data, final TypeDefId type,
			final WorkspaceUserMetadata userMeta, final Provenance provenance,
			final boolean hidden) {
		if (data == null || type == null || provenance == null) {
			throw new IllegalArgumentException(
					"Neither data, provenance, nor type may be null");
		}
		this.id = null;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta == null ?
				new WorkspaceUserMetadata() : userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
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

	public WorkspaceUserMetadata getUserMeta() {
		return userMeta;
	}
	
	public Provenance getProvenance() {
		return provenance;
	}

	public boolean isHidden() {
		return hidden;
	}

	public ResolvedSaveObject resolve(final ValidatedTypedObject rep,
			final Set<Reference> references,
			final List<Reference> provenancerefs,
			final Map<IdReferenceType, Set<RemappedId>> extractedIDs) {
		if (id == null) {
			return new ResolvedSaveObject(this.userMeta, this.provenance,
					this.hidden, rep, references, provenancerefs,
					extractedIDs);
		} else {
			return new ResolvedSaveObject(this.id, this.userMeta,
					this.provenance, this.hidden, rep, references,
					provenancerefs, extractedIDs);
		}
	}
	
	@Override
	public String toString() {
		return "WorkspaceSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
