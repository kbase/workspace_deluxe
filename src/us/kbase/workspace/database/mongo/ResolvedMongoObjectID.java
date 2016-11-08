package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;

import us.kbase.workspace.database.Reference;

/**
 * name is resolved *at the time the database was accessed and is not further
 * updated*
 * 
 * The underlying assumption of this class is all object IDs are unique and all
 * names are unique at the time of resolution. Therefore a set of
 * ResolvedObjectIDs constructed at the same time are all unique in name and id,
 * and removing one or the other field would not cause the number of unique
 * objects to change (as measured by the unique hashcode count, for example).
 * 
 * This is *not* the case for objects generated from different queries.
 * 
 * The version is guaranteed to be equal to or less than the version count in the object document,
 * e.g. under normal circumstances the version must exist.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ResolvedMongoObjectID {
	
	private final ResolvedMongoWSID rwsi;
	private final String name;
	private final Long id;
	private final Integer version;
	private final boolean deleted;
	
	ResolvedMongoObjectID(final ResolvedMongoWSID rwsi, final String name,
			final long id, final int version, final boolean deleted) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		checkObjectName(name);
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.rwsi = rwsi;
		this.name = name;
		this.id = id;
		this.version = version;
		this.deleted = deleted;
	}

	public ResolvedMongoWSID getWorkspaceIdentifier() {
		return rwsi;
	}

	public String getName() {
		return name;
	}

	public Long getId() {
		return id;
	}

	public Integer getVersion() {
		return version;
	}
	
	public boolean isFullyResolved() {
		return false;
	}
	
	public Reference getReference() {
		return new Reference(rwsi.getID(), id, version);
	}

	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public String toString() {
		return "ResolvedMongoObjectID [rwsi=" + rwsi + ", name=" + name
				+ ", id=" + id + ", version=" + version + ", deleted="
				+ deleted + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleted ? 1231 : 1237);
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ResolvedMongoObjectID other = (ResolvedMongoObjectID) obj;
		if (deleted != other.deleted)
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (rwsi == null) {
			if (other.rwsi != null)
				return false;
		} else if (!rwsi.equals(other.rwsi))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}
	
}
