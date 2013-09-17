package us.kbase.workspace.database;

public class ObjectIDResolvedWS {
	
	private final ResolvedWorkspaceID rwsi;
	private final String name;
	private final Integer id;
	private final Integer version;
	
	//no error checking on constructors - should only be generated from an ObjectIdentifier instance
	ObjectIDResolvedWS(ResolvedWorkspaceID rwsi, String name, Integer version) {
		this.rwsi = rwsi;
		this.name = name;
		this.id = null;
		this.version = version;
	}
	
	//no error checking on constructors - should only be generated from an ObjectIdentifier instance
	ObjectIDResolvedWS(ResolvedWorkspaceID rwsi, int id, Integer version) {
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

	public Integer getId() {
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
