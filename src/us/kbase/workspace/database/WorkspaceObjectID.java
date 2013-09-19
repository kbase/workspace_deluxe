package us.kbase.workspace.database;

import static us.kbase.workspace.util.Util.xorNameId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkspaceObjectID {
	
	private final static Pattern INVALID_OBJ_NAMES = 
			Pattern.compile("[^\\w\\|._-]");
	
	private final String name;
	private final Integer id;
	private final Integer version;
	
	public WorkspaceObjectID(String name) {
		checkObjectName(name);
		this.name = name;
		this.id = null;
		this.version = null;
	}
	
	public WorkspaceObjectID(String name, int version) {
		checkObjectName(name);
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.name = name;
		this.id = null;
		this.version = version;
	}
	
	public WorkspaceObjectID(int id) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.name = null;
		this.id = id;
		this.version = null;
	}
	
	public WorkspaceObjectID(int id, int version) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.name = null;
		this.id = id;
		this.version = version;
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
	
	//TODO test woid.create
	public static WorkspaceObjectID create(final String name, 
			final Integer id) {
		xorNameId(name, id, "object");
		if (name != null) {
			return new WorkspaceObjectID(name);
		}
		return new WorkspaceObjectID(id);
	}
	
	public static void checkObjectName(String name) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Object name cannot be null and must have at least one character");
		}
		final Matcher m = INVALID_OBJ_NAMES.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in object name %s: %s", name, m.group()));
		}
	}

	@Override
	public String toString() {
		return "WorkspaceObjectID [name=" + name + ", id=" + id + ", version="
				+ version + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		if (!(obj instanceof WorkspaceObjectID)) {
			return false;
		}
		WorkspaceObjectID other = (WorkspaceObjectID) obj;
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
