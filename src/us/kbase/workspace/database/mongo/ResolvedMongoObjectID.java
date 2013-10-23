package us.kbase.workspace.database.mongo;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;
import us.kbase.workspace.database.ResolvedObjectID;

public class ResolvedMongoObjectID implements ResolvedObjectID {
	
	private final ResolvedMongoWSID rwsi;
	private final String name;
	private final Long id;
	private final Integer version;
	
	ResolvedMongoObjectID(ResolvedMongoWSID rwsi, String name, long id) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		checkObjectName(name);
		this.rwsi = rwsi;
		this.name = name;
		this.id = id;
		this.version = null;
	}
	
	ResolvedMongoObjectID(ResolvedMongoWSID rwsi, String name, long id, int version) {
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

	@Override
	public String toString() {
		return "ResolvedObjectID [rwsi=" + rwsi + ", name=" + name + ", id="
				+ id + ", version=" + version + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((rwsi == null) ? 0 : rwsi.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		if (!(obj instanceof ResolvedMongoObjectID)) {
			return false;
		}
		ResolvedMongoObjectID other = (ResolvedMongoObjectID) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
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
		if (version == null) {
			if (other.version != null) {
				return false;
			}
		} else if (!version.equals(other.version)) {
			return false;
		}
		return true;
	}
	
}
