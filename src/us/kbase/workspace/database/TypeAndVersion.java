package us.kbase.workspace.database;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

public class TypeAndVersion {
	
	private AbsoluteTypeDefId type;
	private long id;
	private int version;

	public TypeAndVersion(final AbsoluteTypeDefId type, final long id,
			final int version) {
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (id < 1) {
			throw new IllegalArgumentException("id must be > 0");
		}
		if (version < 1) {
			throw new IllegalArgumentException("version must be > 0");
		}
		this.type = type;
		this.id = id;
		this.version = version;
	}

	public AbsoluteTypeDefId getType() {
		return type;
	}

	public int getVersion() {
		return version;
	}
	
	public long getID() {
		return id;
	}

	@Override
	public String toString() {
		return "TypeAndVersion [type=" + type + ", id=" + id + ", version="
				+ version + "]";
	}
}
