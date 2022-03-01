package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkNoNullsOrEmpties;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.LinkedList;

import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;

/** A package containing (optionally) a workspace object's data along with provenance and
 * information about the object.
 * 
 * Be sure to call {@link #destroy()} when the object data is no longer needed to release
 * resources.
 * 
 * Note that {@link #hashCode()} and {@link #equals(Object)} are not overridden as this object
 * may contain extremely large amounts of data. Therefore calculating hashcodes and equality could
 * be prohibitively expensive.
 */
// besides that Provenance is mutable, which needs to be fixed at some point
public class WorkspaceObjectData {
	
	private final ByteArrayFileCache data;
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;
	private Reference copied; // TODO NOW make final
	private boolean isCopySourceInaccessible = false; // TODO NOW make final
	private final Map<IdReferenceType, List<String>> extIDs;

	/** Create a data package with only the provenance and other metadata.
	 * @param info information about the object.
	 * @param prov the object's provenance.
	 * @param references references to other workspace objects extracted from the object.
	 * @param copied the source of the object if it was copied from another object. May be null.
	 * @param extIDs any external IDs extracted from the object, mapped by the ID type.
	 */
	// TODO NOW remove
	public WorkspaceObjectData(
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<IdReferenceType, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? Collections.emptyMap() : extIDs;
		this.data = null;
	}
	
	/** Create a data package.
	 * @param data the object data.
	 * @param info information about the object.
	 * @param prov the object's provenance.
	 * @param references references to other workspace objects extracted from the object.
	 * @param copied the source of the object if it was copied from another object. May be null.
	 * @param extIDs any external IDs extracted from the object, mapped by the ID type.
	 */
	// TODO NOW remove
	public WorkspaceObjectData(
			final ByteArrayFileCache data,
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<IdReferenceType, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? Collections.emptyMap() : extIDs;
		this.data = data;
	}

	
	private WorkspaceObjectData(
			final ByteArrayFileCache data, // TODO NOW use an Optional
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied, // TODO NOW use an Optional
			final boolean isCopySourceAccessible,
			final Map<IdReferenceType, List<String>> extIDs) {
		this.data = data;
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.isCopySourceInaccessible = isCopySourceAccessible;
		this.extIDs = Collections.unmodifiableMap(extIDs);
	}
	
	/** Returns information about the object.
	 * @return information about the object.
	 */
	public ObjectInformation getObjectInfo() {
		return info;
	}

	/** Returns the object provenance.
	 * @return the object provenance.
	 */
	public Provenance getProvenance() {
		return prov;
	}
	
	/** Returns any workspace references extracted from the object.
	 * @return a list of workspace references.
	 */
	public List<String> getReferences() {
		return references;
	}
	
	/** Returns the source of the object if copied and accessible.
	 * @return the source of the object.
	 */
	public Reference getCopyReference() {
		return copied;
	}
	
	/** Returns any external IDs extracted from the object, mapped by the ID type.
	 * @return 
	 */
	public Map<IdReferenceType, List<String>> getExtractedIds() {
		return extIDs;
	}
	
	/** Returns the object data.
	 * @return the object data.
	 */
	public ByteArrayFileCache getSerializedData() {
		return data;
	}
	
	/** Returns true if this package contains the object data, false otherwise.
	 * @return true if this package contains the object data.
	 */
	public boolean hasData() {
		return data != null;
	}
	
	/** Removes the copy reference and sets copySourceInacessible to true.
	 */
	public void setCopySourceInaccessible() {
		// TODO NOW this mutates the object. Move to builder and use there.
		copied = null;
		isCopySourceInaccessible = true;
	}
	
	/** Returns true if the object from which this object was copied is accessible to the user
	 * requesting the data.
	 * @return true if the source this object was copied from is accessible.
	 */
	public boolean isCopySourceInaccessible() {
		return isCopySourceInaccessible;
	}
	
	/** Change the path through the object graph that provided access to this object.
	 * @param refpath the object reference path.
	 * @returns a new WorkspaceObject data with an updated reference path.
	 */
	public WorkspaceObjectData updateObjectReferencePath(final List<Reference> refpath) {
		// TODO NOW move to builder and use there.
		final ObjectInformation newoi = info.updateReferencePath(refpath);
		return new WorkspaceObjectData(data, newoi, prov, references, copied, extIDs);
	}
	
	/** Destroys any resources used to store the objects. In the case of
	 * object subsets, also destroys the parent objects. This method should be
	 * called on a set of objects when further processing is no longer
	 * required.
	 */
	public void destroy() {
		if (data != null) {
			data.destroy();
		}
	}
	
	@Override
	public String toString() {  // TODO TEST
		StringBuilder builder = new StringBuilder();
		builder.append("WorkspaceObjectData [data=");
		builder.append(data);
		builder.append(", info=");
		builder.append(info);
		builder.append(", prov=");
		builder.append(prov);
		builder.append(", references=");
		builder.append(references);
		builder.append(", copied=");
		builder.append(copied);
		builder.append(", isCopySourceInaccessible=");
		builder.append(isCopySourceInaccessible);
		builder.append(", extIDs=");
		builder.append(extIDs);
		builder.append("]");
		return builder.toString();
	}
	
	/** Get a builder for a {@link WorkspaceObjectData}.
	 * @param info the {@link ObjectInformation} for the object.
	 * @param prov the {@link Provenance} for the object.
	 * @return the builder.
	 */
	public static Builder getBuilder(final ObjectInformation info, final Provenance prov) {
		return new Builder(info, prov);
	}
	
	/** A builder for a {@link WorkspaceObjectData}. */
	public static class Builder {
		
		private ObjectInformation info;
		private final Provenance prov;
		private ByteArrayFileCache data = null;
		private List<String> references = Collections.emptyList();
		private Reference copied = null;
		private boolean isCopySourceInaccessible = false;
		private final Map<IdReferenceType, List<String>> extIDs = new TreeMap<>();

		private Builder(final ObjectInformation info, final Provenance prov) {
			this.info = requireNonNull(info, "info");
			this.prov = requireNonNull(prov, "prov");
		}
		
		/** Add the object data to the builder. Passing null removes any current data.
		 * @param data the data.
		 * @return this builder.
		 */
		public Builder withData(final ByteArrayFileCache data) {
			this.data = data;
			return this;
		}
		
		/** Add a set of references to other workspace objects that were contained in the object
		 * data to the builder. Passing a null or empty list removes any current references
		 * @param references the references.
		 * @return this builder.
		 */
		public Builder withReferences(final List<String> references) {
			if (references == null || references.isEmpty()) {
				this.references = Collections.emptyList();
			} else {
				// could do some more checking here, but this class is only used in outgoing
				// workspace data, so don't worry about it for now.
				checkNoNullsOrEmpties(references, "references");
				this.references = Collections.unmodifiableList(new LinkedList<>(references));
			}
			return this;
		}
		
		/** Add a reference to the object from which this object was copied, also setting
		 * the inaccessible copy source flag to false.
		 *  Passing null removes any current copy reference.
		 * @param ref the reference to the source of this object.
		 * @return this builder.
		 */
		public Builder withCopyReference(final Reference ref) {
			this.copied = ref;
			this.isCopySourceInaccessible = false;
			return this;
		}
		
		/** Set that this object was copied, but the source object is inaccessible to the current
		 * user. This removes any current copy reference.
		 * @return this builder.
		 */
		public Builder withCopySourceInaccessible() {
			this.copied = null;
			this.isCopySourceInaccessible = true;
			return this;
		}
		
		/** Add external IDs found in the object data to this object. Passing a null or empty list
		 * of IDs will remove any previous IDs for the given type.
		 * @param idType the type of the IDs.
		 * @param ids the IDs.
		 * @return this builder.
		 */
		public Builder withExternalIDs(final IdReferenceType idType, final List<String> ids) {
			requireNonNull(idType, "idType");
			if (ids == null || ids.isEmpty()) {
				this.extIDs.remove(idType);
			} else {
				checkNoNullsOrEmpties(ids, "ids for type " + idType.getType());
				this.extIDs.put(idType, Collections.unmodifiableList(new LinkedList<>(ids)));
			}
			return this;
		}
		
		/** Update the reference path to this object. This will affect the
		 * {@link ObjectInformation} returned from {@link WorkspaceObjectData#getObjectInfo()},
		 * but not provided to
		 * {@link WorkspaceObjectData#getBuilder(ObjectInformation, Provenance)}.
		 * @param refpath the new path. The path must end with this object's reference.
		 * @return this builder.
		 */
		public Builder withUpdatedReferencePath(final List<Reference> refpath) {
			// not sure it *really* makes sense to require the last object in the path to be 
			// this object, but in the practical case it will be, so I suppose checking for
			// that is reasonable.
			this.info = this.info.updateReferencePath(refpath);
			return this;
		}
		
		/** Build the {@link WorkspaceObjectData}.
		 * @return the object data.
		 */
		public WorkspaceObjectData build() {
			return new WorkspaceObjectData(
					data, info, prov, references, copied, isCopySourceInaccessible, extIDs);
		}
	}
}
