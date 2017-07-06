package us.kbase.workspace.database;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;
import static us.kbase.workspace.database.Util.nonNull;

/** An object identifier that has been fully resolved against the data store, e.g. the name
 * and id are available for the workspace and object, and the version is available.
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
public class ResolvedObjectID {
	
	private final ResolvedWorkspaceID rwsi;
	private final String name;
	private final long id;
	private final int version;
	private final boolean deleted;
	
	/** Create a new resolved object identifier.
	 * @param rwsi the identifier of the resolved workspace in which the object resides.
	 * @param id the id of the object.
	 * @param version the version of the object.
	 * @param name the name of the object.
	 * @param deleted true if the object is deleted, false otherwise.
	 */
	public ResolvedObjectID(
			final ResolvedWorkspaceID rwsi,
			final long id,
			final int version,
			final String name,
			final boolean deleted) {
		nonNull(rwsi, "rwsi");
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		checkObjectName(name);
		if (version < 1) {
			throw new IllegalArgumentException("version must be > 0");
		}
		this.rwsi = rwsi;
		this.name = name;
		this.id = id;
		this.version = version;
		this.deleted = deleted;
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

	/** Get the object version.
	 * @return the version.
	 */
	public int getVersion() {
		return version;
	}
	
	/** Get the reference for this object.
	 * @return the reference.
	 */
	public Reference getReference() {
		return new Reference(rwsi.getID(), id, version);
	}

	/** Get whether the object is deleted.
	 * @return true if the object is deleted.
	 */
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public String toString() {
		return "ResolvedObjectID [rwsi=" + rwsi + ", name=" + name
				+ ", id=" + id + ", version=" + version + ", deleted="
				+ deleted + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleted ? 1231 : 1237);
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
		result = prime * result + version;
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
		ResolvedObjectID other = (ResolvedObjectID) obj;
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
		if (version != other.version) {
			return false;
		}
		return true;
	}
	
}
