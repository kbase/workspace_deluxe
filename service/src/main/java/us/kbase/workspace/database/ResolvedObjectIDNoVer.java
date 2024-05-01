package us.kbase.workspace.database;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;
import static us.kbase.workspace.database.Util.nonNull;

/** An object identifier that has been partially resolved against the data store, e.g. the name
 * and id are available for the workspace and object, but the version is not available.
 * 
 * The names are resolved *at the time the database was accessed and are not further
 * updated*.
 * 
 * The underlying assumption of this class is all object IDs are unique and all
 * names are unique at the time of resolution. Therefore a set of
 * ResolvedObjectIDs constructed at the same time are all unique in name and id,
 * and removing one or the other field would not cause the number of unique
 * objects to change (as measured by the unique hashcode count, for example).
 * 
 * This is *not* the case for objects generated from different queries.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ResolvedObjectIDNoVer {
	
	private final ResolvedWorkspaceID rwsi;
	private final String name;
	private final long id;
	private final boolean deleted;
	
	/** Create a resolved object identifier without a version.
	 * @param rwsi the identifier of the resolved workspace in which the object resides.
	 * @param id the id of the object.
	 * @param name the name of the object.
	 * @param deleted true if the object is deleted, false otherwise.
	 */
	public ResolvedObjectIDNoVer(
			final ResolvedWorkspaceID rwsi,
			final long id,
			final String name,
			final boolean deleted) {
		nonNull(rwsi, "rwsi");
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		checkObjectName(name);
		this.rwsi = rwsi;
		this.name = name;
		this.id = id;
		this.deleted = deleted;
	}
	
	/** Create a resolved object identifier without a version from a fully resolved object
	 * identifier.
	 * @param roid a fully resolved object identifier.
	 */
	public ResolvedObjectIDNoVer(final ResolvedObjectID roid) {
		nonNull(roid, "roid");
		this.rwsi = roid.getWorkspaceIdentifier();
		this.name = roid.getName();
		this.id = roid.getId();
		this.deleted = roid.isDeleted();
	}
	
	/** Get the workspace identifier.
	 * @return the workspace identifier.
	 */
	public ResolvedWorkspaceID getWorkspaceIdentifier() {
		return rwsi;
	}

	/** Get the object name.
	 * @return the name.
	 */
	public String getName() {
		return name;
	}

	/** Get the object id.
	 * @return the id.
	 */
	public long getId() {
		return id;
	}
	
	/** Get whether the object is deleted.
	 * @return true if the object is deleted.
	 */
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResolvedObjectIDNoVer [rwsi=");
		builder.append(rwsi);
		builder.append(", name=");
		builder.append(name);
		builder.append(", id=");
		builder.append(id);
		builder.append(", deleted=");
		builder.append(deleted);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleted ? 1231 : 1237);
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
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
		ResolvedObjectIDNoVer other = (ResolvedObjectIDNoVer) obj;
		if (deleted != other.deleted) {
			return false;
		}
		if (id != other.id) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (rwsi == null) {
			if (other.rwsi != null) {
				return false;
			}
		} else if (!rwsi.equals(other.rwsi)) {
			return false;
		}
		return true;
	}
	
}
