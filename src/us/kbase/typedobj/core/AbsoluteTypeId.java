package us.kbase.typedobj.core;

public class AbsoluteTypeId extends TypeId{

	public AbsoluteTypeId(ModuleType type, int majorVersion,
			int minorVersion) {
		super(type, majorVersion, minorVersion);
	}
	
	public static AbsoluteTypeId fromTypeId(TypeId type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (!type.isAbsolute()) {
			throw new IllegalArgumentException("type must be absolute");
		}
		return new AbsoluteTypeId(type.getType(), type.getMajorVersion(),
				type.getMinorVersion());
	}
	
	public static AbsoluteTypeId fromTypeId(TypeId type, int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		if (type.getMajorVersion() == null) {
			throw new IllegalArgumentException(
					"Incoming type major version cannot be null");
		}
		return new AbsoluteTypeId(type.getType(), type.getMajorVersion(),
				minorVersion);
	}
	
	public static AbsoluteTypeId fromTypeId(TypeId type, int majorVersion,
			int minorVersion) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be null");
		}
		return new AbsoluteTypeId(type.getType(), majorVersion, minorVersion);
	}
	
	@Override
	public String toString() {
		return "AbsoluteTypeId [type=" + type + ", majorVersion=" +
				majorVersion + ", minorVersion=" + minorVersion + "]";
	}
}