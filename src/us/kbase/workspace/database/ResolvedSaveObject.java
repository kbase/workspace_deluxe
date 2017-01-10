package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;

public class ResolvedSaveObject {
	
	private final ObjectIDNoWSNoVer id;
	private final WorkspaceUserMetadata userMeta;
	private final Provenance provenance;
	private final boolean hidden;
	private final ValidatedTypedObject rep;
	private final Set<Reference> refs;
	private final List<Reference> provrefs;
	final Map<IdReferenceType, Set<RemappedId>> extractedIDs;
	
	ResolvedSaveObject(
			final ObjectIDNoWSNoVer id,
			final WorkspaceUserMetadata userMeta,
			final Provenance provenance,
			final boolean hidden,
			final ValidatedTypedObject rep,
			final Set<Reference> refs,
			final List<Reference> provenancerefs,
			final Map<IdReferenceType, Set<RemappedId>> extractedIDs) {
		if (id == null || rep == null || refs == null ||
				provenancerefs == null || extractedIDs == null ||
				userMeta == null) {
			throw new IllegalArgumentException(
					"Neither id, rep, refs, extractedIDs, metadata, nor provenancerefs may be null");
		}
		this.id = id;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.rep = rep;
		this.refs = refs;
		this.provrefs = provenancerefs;
		this.extractedIDs = extractedIDs;
	}
	
	public ObjectIDNoWSNoVer getObjectIdentifier() {
		return id;
	}

	public WorkspaceUserMetadata getUserMeta() {
		return userMeta;
	}
	
	/**
	 * Add new metadata, which will overwrite existing metadata fields with the
	 * same name. This method also checks that the size of the metadata is
	 * within the size limit.
	 * @param newUserMeta the new metadata.
	 * @throws MetadataException if the metadata exeeds the allowed size or a
	 * key or value exceeds the allowed key/value size.
	 */
	public void addUserMeta(Map<String,String> newUserMeta) throws MetadataException {
		userMeta.addMetadata(newUserMeta);
	}

	public Provenance getProvenance() {
		return provenance;
	}

	public boolean isHidden() {
		return hidden;
	}

	public ValidatedTypedObject getRep() {
		return rep;
	}

	public Set<Reference> getRefs() {
		return refs;
	}

	public List<Reference> getProvRefs() {
		return provrefs;
	}
	
	public Map<IdReferenceType, Set<RemappedId>> getExtractedIDs() {
		return extractedIDs;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResolvedSaveObject [id=");
		builder.append(id);
		builder.append(", userMeta=");
		builder.append(userMeta);
		builder.append(", provenance=");
		builder.append(provenance);
		builder.append(", hidden=");
		builder.append(hidden);
		builder.append(", rep=");
		builder.append(rep);
		builder.append(", refs=");
		builder.append(refs);
		builder.append(", provrefs=");
		builder.append(provrefs);
		builder.append(", extractedIDs=");
		builder.append(extractedIDs);
		builder.append("]");
		return builder.toString();
	}
}
