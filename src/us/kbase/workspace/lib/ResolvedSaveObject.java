package us.kbase.workspace.lib;

import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;

import static us.kbase.workspace.database.Util.checkSize;

public class ResolvedSaveObject {
	
	private final ObjectIDNoWSNoVer id;
	private Map<String, String> userMeta; // userMeta data is non-final because it can be appended
	private final Provenance provenance;
	private final boolean hidden;
	private final TypedObjectValidationReport rep;
	private final Set<Reference> refs;
	private final List<Reference> provrefs;
	
	ResolvedSaveObject(final ObjectIDNoWSNoVer id,
			final Map<String, String> userMeta, final Provenance provenance,
			final boolean hidden, final TypedObjectValidationReport rep,
			final Set<Reference> refs, final List<Reference> provenancerefs) {
		if (id == null || rep == null || refs == null ||
				provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither id, rep, refs, nor provenancerefs may be null");
		}
		this.id = id;
		this.userMeta = userMeta;
		this.provenance = provenance;
		this.hidden = hidden;
		this.rep = rep;
		this.refs = refs;
		this.provrefs = provenancerefs;
	}
	
	ResolvedSaveObject(final Map<String, String> userMeta,
			final Provenance provenance, final boolean hidden,
			final TypedObjectValidationReport rep, final Set<Reference> refs,
			final List<Reference> provenancerefs) {
		if (rep == null || refs == null || provenancerefs == null) {
			throw new IllegalArgumentException(
					"Neither rep, refs, nor provenancerefs may be null");
		}
		this.id = null;
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
	public Map<String, String> getUserMeta() {
		return userMeta;
	}
	
	/**
	 * Add new metadata, which will overwrite existing metadata fields with the same name.
	 * This method also checks that the size of the metadata is within the size limit.
	 */
	public void addUserMeta(Map<String,String> newUserMeta) {
		if(userMeta==null) userMeta = newUserMeta;
		else userMeta.putAll(newUserMeta);
		checkSize(userMeta, "Metadata", WorkspaceSaveObject.getMaxUserMetaSize());
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
		return "ResolvedSaveObject [id=" + id + ", userMeta=" + userMeta +
				", provenance=" + provenance + ", hidden=" + hidden +
				", rep=" + rep + ", refs=" + refs +
				", provrefs=" + provrefs + "]";
	}
}
