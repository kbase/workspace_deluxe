package us.kbase.typedobj.core;

import static us.kbase.typedobj.util.TypeUtils.checkString;

public class TypeDefId {
	
	private final static String TYPE_VER_SEP = "-";
	private final static String TYPE_SEP = ".";
	private final static String VER_SEP = ".";
	private final static String TYPE_SEP_REGEX = "\\" + TYPE_SEP;
	private final static String VER_SEP_REGEX = "\\" + VER_SEP;
	
	final TypeDefName type;
	final Integer majorVersion;
	final Integer minorVersion;

	public TypeDefId(TypeDefName type, int majorVersion, int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (majorVersion < 0 || minorVersion < 0) {
			throw new IllegalArgumentException("Version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}
	
	public TypeDefId(TypeDefName type, int majorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (majorVersion < 0) {
			throw new IllegalArgumentException("Version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = null;
	}
	
	public TypeDefId(TypeDefName type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		this.type = type;
		this.majorVersion = null;
		this.minorVersion = null;
	}
	
	private static final String TYPE_VER_ERR = 
			"Type version string %s could not be parsed to a version";
	
	public TypeDefId(String moduletype, String typeversion) {
		checkString(moduletype, "Moduletype");
		if (typeversion != null && typeversion.equals("")) {
			throw new IllegalArgumentException("Typeversion cannot be an empty string");
		}
		final String[] t = moduletype.split(TYPE_SEP_REGEX);
		if (t.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Type %s could not be split into a module and name",
					moduletype));
		}
		type = new TypeDefName(t[0], t[1]);
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
	
	public TypeDefId(String moduletype) {
		this(moduletype, null);
	}
	
	public static TypeDefId fromTypeString(String typestring) {
		checkString(typestring, "Typestring");
		final String[] ts = typestring.split(TYPE_VER_SEP);
		if (ts.length == 1) {
			return new TypeDefId(ts[0]);
		}
		if (ts.length == 2) {
			return new TypeDefId(ts[0], ts[1]);
		}
		throw new IllegalArgumentException(String.format(
				"Could not parse typestring %s into module/type and version portions",
				typestring));
	}
	
	public TypeDefName getType() {
		return type;
	}
	
	public Integer getMajorVersion() {
		return majorVersion;
	}
	
	public Integer getMinorVersion() {
		return minorVersion;
	}
	
	public boolean isAbsolute() {
		return minorVersion != null;
	}
	
	public String getTypeString() {
		String t = type.getTypeString();
		final String v = getVerString();
		if (v != null) {
			t += TYPE_VER_SEP + v;
		}
		return t;
	}
	
	public String getVerString() {
		if (majorVersion == null) {
			return null;
		}
		String t = "" + majorVersion;
		if (minorVersion == null) {
			return t;
		}
		return t + VER_SEP + minorVersion;
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
		if (!(obj instanceof TypeDefId)) {
			return false;
		}
		TypeDefId other = (TypeDefId) obj;
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
