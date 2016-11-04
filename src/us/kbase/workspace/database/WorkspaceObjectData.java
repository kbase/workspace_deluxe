package us.kbase.workspace.database;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;

/** A package containing (optionally) a workspace object's data along with provenance and
 * information about the object.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceObjectData {
	
	private final ByteArrayFileCache data;
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;
	private Reference copied;
	private boolean isCopySourceInaccessible = false;
	private final Map<String, List<String>> extIDs;

	/** Create a data package with only the provenance and other metadata.
	 * @param info information about the object.
	 * @param prov the object's provenance.
	 * @param references references to other workspace objects extracted from the object.
	 * @param copied the source of the object if it was copied from another object. May be null.
	 * @param extIDs any external IDs extracted from the object, mapped by the ID type.
	 */
	public WorkspaceObjectData(
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<String, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? new HashMap<String, List<String>>() :
			extIDs;
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
	public WorkspaceObjectData(
			final ByteArrayFileCache data,
			final ObjectInformation info,
			final Provenance prov,
			final List<String> references,
			final Reference copied,
			final Map<String, List<String>> extIDs) {
		if (info == null || prov == null || references == null) {
			throw new IllegalArgumentException(
					"references, prov and info cannot be null");
		}
		this.info = info;
		this.prov = prov;
		this.references = references;
		this.copied = copied;
		this.extIDs = extIDs == null ? new HashMap<String, List<String>>() : extIDs;
		this.data = data;
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
	public Map<String, List<String>> getExtractedIds() {
		return extIDs;
		//could make this immutable I suppose
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
	void setCopySourceInaccessible() {
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
	WorkspaceObjectData updateObjectReferencePath(final List<Reference> refpath) {
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
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
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
}
