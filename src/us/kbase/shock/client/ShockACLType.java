package us.kbase.shock.client;

public class ShockACLType {

	protected final String acl;
	public final String aclType;
	public static final String READ = "read";
	public static final String WRITE = "write";
	public static final String OWNER = "owner";
	public static final String DELETE = "delete";
	
	public ShockACLType(String type) {
		if (type == "all") {
			acl = "/acl/";
		} else if (type == "read" || type == "write" || type == "delete" ||
				type == "owner") {
			acl = "/acl/" + type + "/";
		} else {
			throw new IllegalArgumentException(type + " is not a valid acl type");
		}
		aclType = type;
	}
}
