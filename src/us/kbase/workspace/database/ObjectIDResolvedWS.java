package us.kbase.workspace.database;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;

public class ObjectIDResolvedWS {
	
	private final ResolvedWorkspaceID rwsi;
	private final String name;
	private final Long id;
	private final Integer version;
	
	public ObjectIDResolvedWS(final ResolvedWorkspaceID rwsi,
			final ObjectIDNoWSNoVer oid) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (oid == null) {
			throw new IllegalArgumentException("oid cannot be null");
		}
		this.rwsi = rwsi;
		if (oid.getId().isPresent()) {
			this.id = oid.getId().get();
			this.name = null;
		} else {
			this.name = oid.getName().get();
			this.id = null;
		}
		this.version = null;
	}
	
	public ObjectIDResolvedWS(final ResolvedWorkspaceID rwsi,
			final String name) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		checkObjectName(name);
		this.rwsi = rwsi;
		this.name = name;
		this.id = null;
		this.version = null;
	}
	
	public ObjectIDResolvedWS(final ResolvedWorkspaceID rwsi,
			final String name, final int version) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		checkObjectName(name);
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.rwsi = rwsi;
		this.name = name;
		this.id = null;
		this.version = version;
	}
	
	public ObjectIDResolvedWS(final ResolvedWorkspaceID rwsi, final long id) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.rwsi = rwsi;
		this.name = null;
		this.id = id;
		this.version = null;
	}
	
	public ObjectIDResolvedWS(final ResolvedWorkspaceID rwsi, final long id,
			final int version) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.rwsi = rwsi;
		this.name = null;
		this.id = id;
		this.version = version;
	}

	public ResolvedWorkspaceID getWorkspaceIdentifier() {
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
	
	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}
	
	@Override
	public String toString() {
		return "ObjectIDResolvedWS [rwsi=" + rwsi + ", name=" + name + ", id="
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
		if (!(obj instanceof ObjectIDResolvedWS)) {
			return false;
		}
		ObjectIDResolvedWS other = (ObjectIDResolvedWS) obj;
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
