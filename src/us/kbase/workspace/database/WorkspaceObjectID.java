package us.kbase.workspace.database;

import static us.kbase.workspace.kbase.ArgUtils.xorNameId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkspaceObjectID {
	
	private final static Pattern INVALID_OBJ_NAMES = 
			Pattern.compile("[^\\w\\|._-]");
	
	private final String name;
	private final Long id;
	
	public WorkspaceObjectID(String name) {
		checkObjectName(name);
		this.name = name;
		this.id = null;
	}
	
	public WorkspaceObjectID(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.name = null;
		this.id = id;
	}
	
	public String getName() {
		return name;
	}

	public Long getId() {
		return id;
	}
	
	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}
	
	public static WorkspaceObjectID create(final String name, 
			final Long id) {
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
		return "WorkspaceObjectID [name=" + name + ", id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		return true;
	}

}
