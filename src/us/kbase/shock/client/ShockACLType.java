package us.kbase.shock.client;


/** 
 * <p>Class that models the type of access control list (ACL) to retrieve from
 * shock.</p>
 * 
 * Typical usage:<br/>
 * 
 * <code>ShockACLType type = new ShockACLType(ShockACLType.READ);</code>
 * @author gaprice@lbl.gov
 *
 */
public class ShockACLType {

	final String acl;
	private final String aclType;
	/** Denotes a read ACL. */
	public static final String READ = "read";
	/** Denotes a write ACL. */
	public static final String WRITE = "write";
	/** Denotes an owner ACL. */
	public static final String OWNER = "owner";
	/** Denotes a delete ACL. */
	public static final String DELETE = "delete";
	/** Denotes all acls. */
	public static final String ALL = "all";
	
	/** 
	 * Constructs a <code>ShockACLType</code> that represents all ACLs.
	 */
	public ShockACLType() {
		acl = "/acl/";
		aclType = "all";
	}
	
	/** 
	 * Constructs a <code>ShockACLType</code>.
	 * @param type the type of ACL to build. Valid types are <code>"all"</code>,
	 * <code>"read"</code>, <code>"write"</code>, <code>"owner"</code>,
	 * and <code>"delete"</code>.
	 */
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
	
	/** 
	 * Returns the ACL type.
	 * @return the type of ACLS this object represents.
	 */
	public String getType() {
		return aclType;
	}
}
