package us.kbase.workspace.database;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/** Various information about a workspace object, including the workspace and object names and
 * ids, the object version, they type, and more.
 * @author gaprice@lbl.gov
 *
 */
public class ObjectInformation {
	
	final private long id;
	final private String name;
	final private String type;
	final private long savedDate;
	final private int version;
	final private WorkspaceUser savedBy;
	final private long workspaceID;
	final private String workspaceName;
	final private String chksum;
	final private long size;
	final private UncheckedUserMetadata meta;
	final private List<Reference> refpath;
	
	// TODO CODE make a builder, even though only meta is optional. More readable, easier to add new optional fields.
	// TODO CODE use a typedef class vs a string.
	// TODO CODE md5 should use md5 class? Or is this a memory saving thing
	
	/** Create a new ObjectInformation.
	 * @param id the object id.
	 * @param name the object name.
	 * @param typeString the absolute type of the object as a string.
	 * @param savedDate the date the object was saved or copied.
	 * @param version the version of the object.
	 * @param savedBy the user that saved or copied the object.
	 * @param workspaceID the resolved workspace identifier containing the object.
	 * @param chksum the md5 checksum of the object when serialized to a JSON string with sorted
	 * keys.
	 * @param size the size of the object when serialized to a JSON string.
	 * @param meta the user provided and autogenerated metadata for the object.
	 */
	public ObjectInformation(
			final long id,
			final String name,
			final String typeString,
			final Date savedDate, //TODO CODE use instant
			final int version,
			final WorkspaceUser savedBy,
			final ResolvedWorkspaceID workspaceID,
			final String chksum,
			final long size,
			final UncheckedUserMetadata meta) {
		//no error checking for now, add if needed
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		if (version < 1) {
			throw new IllegalArgumentException("version must be > 0");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be > 0");
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("name is null or empty");
		}
		if (typeString == null || typeString.isEmpty()) { // could switch to absolutetypedef input
			throw new IllegalArgumentException("typeString is null or empty");
		}
		if (chksum == null|| chksum.isEmpty()) { // could switch to MD5
			throw new IllegalArgumentException("chksum is null or empty");
		}
		if (savedDate == null) {
			throw new NullPointerException("savedDate");
		}
		if (savedBy == null) {
			throw new NullPointerException("savedBy");
		}
		if (workspaceID == null) {
			throw new NullPointerException("workspaceID");
		}
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.savedDate = savedDate.getTime();
		this.version = version;
		this.savedBy = savedBy;
		this.workspaceID = workspaceID.getID();
		this.workspaceName = workspaceID.getName();
		this.chksum = chksum;
		this.size = size;
		this.meta = meta;
		//could leave this as null and construct as needed to save mem, but meh for now
		final List<Reference> refs = new LinkedList<>();
		refs.add(new Reference(workspaceID.getID(), id, version));
		this.refpath = Collections.unmodifiableList(refs);
	}
	
	private ObjectInformation(
			final long id,
			final String name,
			final String typeString,
			final long savedDate,
			final int version,
			final WorkspaceUser savedBy,
			final long workspaceID,
			final String workspaceName,
			final String chksum,
			final long size,
			final UncheckedUserMetadata meta,
			final List<Reference> refpath) {
		//no error checking for now, add if needed
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.savedDate = savedDate;
		this.version = version;
		this.savedBy = savedBy;
		this.workspaceID = workspaceID;
		this.workspaceName = workspaceName;
		this.chksum = chksum;
		this.size = size;
		this.meta = meta;
		this.refpath = Collections.unmodifiableList(refpath);
	}
	
	/** Returns the id of the object.
	 * @return the id of the object.
	 */
	public long getObjectId() {
		return id;
	}
	
	/** Returns the name of the object.
	 * @return the name of the object.
	 */
	public String getObjectName() {
		return name;
	}
	
	/** Returns the type of the object as a string. This will always be the absolute type.
	 * @return the object type.
	 */
	public String getTypeString() {
		return type;
	}
	
	/** Returns the size of the object when serialized to a JSON string in bytes.
	 * @return the size of the object in bytes.
	 */
	public long getSize() {
		return size;
	}
	
	/** Returns the time the object was saved.
	 * @return the time the object was saved.
	 */
	public Date getSavedDate() {
		return new Date(savedDate);
	}
	
	/** Returns the version of the object.
	 * @return the version of the object.
	 */
	public int getVersion() {
		return version;
	}
	
	/** Returns the user that saved or copied the object.
	 * @return the user that saved or copied the object.
	 */
	public WorkspaceUser getSavedBy() {
		return savedBy;
	}
	
	/** Returns the id of the workspace in which the object is stored.
	 * @return the workspace id.
	 */
	public long getWorkspaceId() {
		return workspaceID;
	}
	
	/** Returns the name of the workspace in which the object is stored.
	 * @return the workspace name.
	 */
	public String getWorkspaceName() {
		return workspaceName;
	}
	
	/** Returns the md5 checksum of the object when serialized to a JSON string with sorted keys.
	 * @return the md5 checksum.
	 */
	public String getCheckSum() {
		return chksum;
	}
	
	/** Returns the user supplied and automatically generated metadata for the object.
	 * @return the object metadata.
	 */
	public UncheckedUserMetadata getUserMetaData() {
		return meta;
	}

	/** Returns the resolved reference path to this object from a user-accessible object. There may
	 * be more than one possible path to an object; only the path used to verify accessibility for
	 * the current access is provided. If the object is directly user-accessible the path will only
	 * contain the object reference.
	 * @return the reference path to the object.
	 */
	public List<Reference> getReferencePath() {
		return refpath;
	}
	
	/** Updates the reference path to the path supplied and returns a new ObjectInformation with
	 * that reference path.
	 * @param refpath the reference path that should replace the current reference path. The last
	 * entry in the reference path must be identical to the last entry in the current reference
	 * path.
	 * @return a new ObjectInformation with an updated reference path.
	 */
	public ObjectInformation updateReferencePath(List<Reference> refpath) {
		if (refpath == null || refpath.isEmpty()) {
			throw new IllegalArgumentException("refpath cannot be null or empty");
		}
		if (!getLast(refpath).equals(getLast(this.refpath))) {
			throw new IllegalArgumentException(
					"refpath must end with the same reference as the current refpath");
		}
		return new ObjectInformation(id, name, type, savedDate, version, savedBy, workspaceID,
				workspaceName, chksum, size, meta, refpath);
	}
	
	private Reference getLast(final List<Reference> refpath) {
		return refpath.get(refpath.size() - 1);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectInformation [id=");
		builder.append(id);
		builder.append(", name=");
		builder.append(name);
		builder.append(", type=");
		builder.append(type);
		builder.append(", savedDate=");
		builder.append(savedDate);
		builder.append(", version=");
		builder.append(version);
		builder.append(", savedBy=");
		builder.append(savedBy);
		builder.append(", workspaceID=");
		builder.append(workspaceID);
		builder.append(", workspaceName=");
		builder.append(workspaceName);
		builder.append(", chksum=");
		builder.append(chksum);
		builder.append(", size=");
		builder.append(size);
		builder.append(", meta=");
		builder.append(meta);
		builder.append(", refpath=");
		builder.append(refpath);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chksum == null) ? 0 : chksum.hashCode());
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((meta == null) ? 0 : meta.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((refpath == null) ? 0 : refpath.hashCode());
		result = prime * result + ((savedBy == null) ? 0 : savedBy.hashCode());
		result = prime * result + (int) (savedDate ^ (savedDate >>> 32));
		result = prime * result + (int) (size ^ (size >>> 32));
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + version;
		result = prime * result + (int) (workspaceID ^ (workspaceID >>> 32));
		result = prime * result + ((workspaceName == null) ? 0 : workspaceName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ObjectInformation other = (ObjectInformation) obj;
		if (chksum == null) {
			if (other.chksum != null) {
				return false;
			}
		} else if (!chksum.equals(other.chksum)) {
			return false;
		}
		if (id != other.id) {
			return false;
		}
		if (meta == null) {
			if (other.meta != null) {
				return false;
			}
		} else if (!meta.equals(other.meta)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (refpath == null) {
			if (other.refpath != null) {
				return false;
			}
		} else if (!refpath.equals(other.refpath)) {
			return false;
		}
		if (savedBy == null) {
			if (other.savedBy != null) {
				return false;
			}
		} else if (!savedBy.equals(other.savedBy)) {
			return false;
		}
		if (savedDate != other.savedDate) {
			return false;
		}
		if (size != other.size) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		if (version != other.version) {
			return false;
		}
		if (workspaceID != other.workspaceID) {
			return false;
		}
		if (workspaceName == null) {
			if (other.workspaceName != null) {
				return false;
			}
		} else if (!workspaceName.equals(other.workspaceName)) {
			return false;
		}
		return true;
	}
	
}
