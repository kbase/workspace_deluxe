package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.provenance.Provenance;

public class WorkspaceSaveObject {
	
	// TODO TEST unit tests
	// TODO JAVADOC
	
	// Unfortunately UObjects aren't necessarily immutable so there's no way to make this truly
	// immutable - although maybe could check that the UObject wraps a JsonTokenStream in which
	// case it should be immutable (double check)
	
	private final ObjectIDNoWSNoVer id;
	private final UObject data;
	private final TypeDefId type;
	private final WorkspaceUserMetadata userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	
	public WorkspaceSaveObject(
			final ObjectIDNoWSNoVer id,
			final Object data,
			final TypeDefId type,
			final WorkspaceUserMetadata userMeta,
			final Provenance provenance,
			final boolean hidden) {
		if (id == null || data == null || type == null || provenance == null) {
			throw new IllegalArgumentException(
					"Neither id, provenance, data, nor type may be null");
		}
		this.id = id;
		this.data = transformData(data);
		this.type = type;
		this.userMeta = userMeta == null ? new WorkspaceUserMetadata() : userMeta;
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

	// provrefs *must* be in the same ordering as they are in the provenance actions
	public ResolvedSaveObject resolve(
			final ResolvedWorkspaceID wsid,
			final ValidatedTypedObject rep,
			final Set<Reference> references,
			final List<Reference> provenancerefs,
			final Map<IdReferenceType, Set<RemappedId>> extractedIDs) {
		return new ResolvedSaveObject(
				this.id,
				this.userMeta,
				this.provenance.updateWorkspaceID(wsid.getID()),
				this.hidden,
				rep,
				references,
				provenancerefs,
				extractedIDs);
	}
	
	@Override
	public String toString() {
		return "WorkspaceSaveObject [id=" + id + ", data=" + data + ", type="
				+ type + ", userMeta=" + userMeta + ", provenance="
				+ provenance + ", hidden=" + hidden + "]";
	}
}
