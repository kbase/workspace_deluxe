package us.kbase.typedobj.core;

public class AbsoluteTypeDefId extends TypeDefId {

	public AbsoluteTypeDefId(final TypeDefName type, final int majorVersion,
			final int minorVersion) {
		super(type, majorVersion, minorVersion);
	}

	public AbsoluteTypeDefId(final TypeDefName type, final String md5) {
		super(type, md5);
	}

	public static AbsoluteTypeDefId fromTypeId(final TypeDefId type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (!type.isAbsolute()) {
			throw new IllegalArgumentException("Type must be absolute");
		}
		return new AbsoluteTypeDefId(type.getType(), type.getMajorVersion(),
				type.getMinorVersion());
	}
	
	public static AbsoluteTypeDefId fromTypeId(final TypeDefId type,
			final int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (type.getMajorVersion() == null) {
			throw new IllegalArgumentException(
					"Incoming type major version cannot be null");
		}
		return new AbsoluteTypeDefId(type.getType(), type.getMajorVersion(),
				minorVersion);
	}
	
	public static AbsoluteTypeDefId fromTypeId(final TypeDefId type,
			final int majorVersion, final int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		return new AbsoluteTypeDefId(type.getType(), majorVersion, minorVersion);
	}
	
	public static AbsoluteTypeDefId fromAbsoluteTypeString(final String type) {
		return fromTypeId(TypeDefId.fromTypeString(type));
	}
	
	@Override
	public String toString() {
		return "AbsoluteTypeDefId [type=" + type + ", majorVersion=" +
				majorVersion + ", minorVersion=" + minorVersion + ", md5=" + md5 + "]";
	}
}