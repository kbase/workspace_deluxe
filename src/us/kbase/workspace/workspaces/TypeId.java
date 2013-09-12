package us.kbase.workspace.workspaces;

public class TypeId {
	
	private final static String TYPE_VER_SEP = "-";
	private final static String TYPE_SEP = ".";
	private final static String VER_SEP = ".";
	private final static String TYPE_SEP_REGEX = "\\" + TYPE_SEP;
	private final static String VER_SEP_REGEX = "\\" + VER_SEP;
	
	final WorkspaceType type;
	final Integer majorVersion;
	final Integer minorVersion;

	public TypeId(WorkspaceType type, int majorVersion, int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (majorVersion < 0 || minorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}
	
	public TypeId(WorkspaceType type, int majorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (majorVersion < 0) {
			throw new IllegalArgumentException("version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = null;
	}
	
	public TypeId(WorkspaceType type) {
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		this.type = type;
		this.majorVersion = null;
		this.minorVersion = null;
	}
	
	private static final String TYPE_VER_ERR = 
			"Type version string %s could not be parsed to a version";
	
	public TypeId(String moduletype, String typeversion) {
		if (moduletype == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		final String[] t = moduletype.split(TYPE_SEP_REGEX);
		if (t.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Type %s could not be split into a module and name",
					moduletype));
		}
		type = new WorkspaceType(t[0], t[1]);
		if (typeversion == null) {
			majorVersion = null;
			minorVersion = null;
			return;
		}
		final String[] v = typeversion.split(VER_SEP_REGEX);
		if (v.length != 1 && v.length != 2) {
			throw new IllegalArgumentException(String.format(TYPE_VER_ERR,
					typeversion));
		}
		try {
			majorVersion = Integer.parseInt(v[0]);
		} catch (NumberFormatException ne) {
			throw new IllegalArgumentException(String.format(TYPE_VER_ERR,
					typeversion));
		}
		if (v.length == 2) {
			try {
				minorVersion = Integer.parseInt(v[1]);
			} catch (NumberFormatException ne) {
				throw new IllegalArgumentException(String.format(TYPE_VER_ERR,
						typeversion));
			}
		} else {
			minorVersion = null;
		}
	}
	
	public TypeId(String moduletype) {
		this(moduletype, null);
	}
	
	public static TypeId fromTypeString(String typestring) {
		final String[] ts = typestring.split(TYPE_VER_SEP);
		if (ts.length == 1) {
			return new TypeId(ts[0]);
		}
		if (ts.length == 2) {
			return new TypeId(ts[0], ts[1]);
		}
		throw new IllegalArgumentException(String.format(
				"Could not parse typestring %s into module/type and version portions",
				typestring));
	}
	
	public WorkspaceType getType() {
		return type;
	}
	
	public Integer getMajorVersion() {
		return majorVersion;
	}
	
	public Integer getMinorVersion() {
		return minorVersion;
	}
	
	public boolean isAbsolute() {
		return majorVersion != null && minorVersion != null;
	}
	
	public String getTypeString() {
		String t = type.getModule() + TYPE_SEP + type.getName();
		if (majorVersion != null) {
			t += TYPE_VER_SEP + majorVersion; 
		}
		if (minorVersion != null) {
			t += VER_SEP + minorVersion;
		}
		return t;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((majorVersion == null) ? 0 : majorVersion.hashCode());
		result = prime * result
				+ ((minorVersion == null) ? 0 : minorVersion.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		if (!(obj instanceof TypeId)) {
			return false;
		}
		TypeId other = (TypeId) obj;
		if (majorVersion == null) {
			if (other.majorVersion != null) {
				return false;
			}
		} else if (!majorVersion.equals(other.majorVersion)) {
			return false;
		}
		if (minorVersion == null) {
			if (other.minorVersion != null) {
				return false;
			}
		} else if (!minorVersion.equals(other.minorVersion)) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "TypeId [type=" + type + ", majorVersion=" + majorVersion
				+ ", minorVersion=" + minorVersion + "]";
	}

}
