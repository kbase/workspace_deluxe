package us.kbase.typedobj.core;

import static us.kbase.common.utils.StringUtils.checkString;

public class TypeDefId {
	
	public final static String TYPE_VER_SEP = "-";
	public final static String TYPE_SEP = ".";
	public final static String VER_SEP = ".";
	private final static String TYPE_SEP_REGEX = "\\" + TYPE_SEP;
	private final static String VER_SEP_REGEX = "\\" + VER_SEP;
	
	protected final TypeDefName type;
	protected final Integer majorVersion;
	protected final Integer minorVersion;
	// TODO CODE there should probably be different classes for MD5 and standard types
	protected final MD5 md5;
	
	// TODO JAVADOC
	// TODO TEST clean up and complete tests. 

	public TypeDefId(final TypeDefName type, final int majorVersion,
			final int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (majorVersion < 0 || minorVersion < 0) {
			throw new IllegalArgumentException("Version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
		this.md5 = null;
	}
	
	public TypeDefId(final TypeDefName type, final int majorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (majorVersion < 0) {
			throw new IllegalArgumentException("Version numbers must be >= 0");
		}
		this.type = type;
		this.majorVersion = majorVersion;
		this.minorVersion = null;
		this.md5 = null;
	}
	
	public TypeDefId(final TypeDefName type, final MD5 md5) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (md5 == null) {
			throw new IllegalArgumentException("md5 cannot be null");
		}
		this.type = type;
		this.majorVersion = null;
		this.minorVersion = null;
		this.md5 = md5;
	}

	public TypeDefId(final TypeDefName type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		this.type = type;
		this.majorVersion = null;
		this.minorVersion = null;
		this.md5 = null;
	}
	
	private static final String TYPE_VER_ERR = 
			"Type version string %s could not be parsed to a version";
	
	public TypeDefId(final String moduletype, final String typeversion) {
		checkString(moduletype, "Moduletype");
		if (typeversion != null && typeversion.equals("")) {
			throw new IllegalArgumentException(
					"Typeversion cannot be an empty string");
		}
		final String[] t = moduletype.split(TYPE_SEP_REGEX);
		if (t.length != 2) {
			throw new IllegalArgumentException(String.format(
					"Type %s could not be split into a module and name",
					moduletype));
		}
		type = new TypeDefName(t[0], t[1]);
		if (typeversion == null) {
			md5 = null;
			majorVersion = null;
			minorVersion = null;
			return;
		}
		final String[] v = typeversion.split(VER_SEP_REGEX);
		if (v.length != 1 && v.length != 2) {
			throw new IllegalArgumentException(String.format(TYPE_VER_ERR,
					typeversion));
		}
		if (v.length == 1 && typeversion.length() == 32) {
			try {
				md5 = new MD5(typeversion); //safe to assume it's an MD5 at this point
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(
						"Type version string could not be parsed to a version: "
						 + iae.getLocalizedMessage());
			}
			majorVersion = null;
			minorVersion = null;
		} else {
			md5 = null;
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
	}
	
	public TypeDefId(final String moduletype) {
		this(moduletype, null);
	}
	
	public static TypeDefId fromTypeString(final String typestring) {
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
	
	public MD5 getMd5() {
		return md5;
	}
	
	public boolean isAbsolute() {
		return (minorVersion != null) || (md5 != null);
	}
	
	//TODO TEST getTypePrefix()
	public String getTypePrefix() {
		String t = type.getTypeString() + TYPE_VER_SEP;
		if (majorVersion == null) {
			if (md5 != null) {
				return t + md5;
			} else {
				return t;
			}
		}
		t += majorVersion + TYPE_SEP;
		if (minorVersion == null) {
			return t;
		}
		return t + minorVersion;
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
			if (md5 != null) {
				return md5.getMD5();
			} else {
				return null;
			}
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
		result = prime * result + ((md5 == null) ? 0 : md5.hashCode());
		result = prime * result
				+ ((minorVersion == null) ? 0 : minorVersion.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		TypeDefId other = (TypeDefId) obj;
		if (majorVersion == null) {
			if (other.majorVersion != null)
				return false;
		} else if (!majorVersion.equals(other.majorVersion))
			return false;
		if (md5 == null) {
			if (other.md5 != null)
				return false;
		} else if (!md5.equals(other.md5))
			return false;
		if (minorVersion == null) {
			if (other.minorVersion != null)
				return false;
		} else if (!minorVersion.equals(other.minorVersion))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TypeDefId [type=" + type + ", majorVersion=" + majorVersion
				+ ", minorVersion=" + minorVersion + ", md5=" + md5 + "]";
	}

}
