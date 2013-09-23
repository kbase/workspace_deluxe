package us.kbase.typedobj.core;

public class AbsoluteTypeDefId extends TypeDefId{

	public AbsoluteTypeDefId(TypeDefName type, int majorVersion,
			int minorVersion) {
		super(type, majorVersion, minorVersion);
	}
	
	public static AbsoluteTypeDefId fromTypeId(TypeDefId type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (!type.isAbsolute()) {
			throw new IllegalArgumentException("type must be absolute");
		}
		return new AbsoluteTypeDefId(type.getType(), type.getMajorVersion(),
				type.getMinorVersion());
	}
	
	public static AbsoluteTypeDefId fromTypeId(TypeDefId type, int minorVersion) {
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
	
	public static AbsoluteTypeDefId fromTypeId(TypeDefId type, int majorVersion,
			int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		return new AbsoluteTypeDefId(type.getType(), majorVersion, minorVersion);
	}
	
	@Override
	public String toString() {
		return "AbsoluteTypeId [type=" + type + ", majorVersion=" +
				majorVersion + ", minorVersion=" + minorVersion + "]";
	}
}