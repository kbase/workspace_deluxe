package us.kbase.workspace.database.mongo;

public class Fields {

	public static final String FIELD_SEP = ".";

	public static final String MONGO_ID = "_id";

	// workspace counter fields
	public static final String CNT_ID = "id";
	public static final String CNT_ID_VAL = "wscounter";
	public static final String CNT_NUM = "num";

	// workspace fields
	public static final String WS_OWNER = "owner";
	public static final String WS_ID = "ws";
	public static final String WS_MODDATE = "moddate";
	public static final String WS_NAME = "name";
	public static final String WS_DEL = "del";
	public static final String WS_NUMOBJ = "numObj";
	public static final String WS_DESC = "desc";
	public static final String WS_LOCKED = "lock";
	public static final String WS_META = "meta";
	/* since 0.5.0
	 * for workspaces that are being cloned and should not be accessed by any
	 * other process. Either true or omitted, never false.
	 */
	public static final String WS_CLONING = "cloning";

	// workspace acl fields
	public static final String ACL_WSID = "id";
	public static final String ACL_PERM = "perm";
	public static final String ACL_USER = "user";

	// workspace object fields
	public static final String OBJ_WS_ID = "ws";
	public static final String OBJ_ID = "id";
	public static final String OBJ_NAME = "name";
	public static final String OBJ_MODDATE = "moddate";
	public static final String OBJ_VCNT = "numver";
	public static final String OBJ_DEL = "del";
	public static final String OBJ_HIDE = "hide";
	public static final String OBJ_REFCOUNTS = "refcnt";
	public static final String OBJ_LATEST = "latest";

	// workspace version fields
	public static final String VER_WS_ID = "ws";
	public static final String VER_ID = "id";
	public static final String VER_VER = "ver";
	public static final String VER_SAVEDBY = "savedby";
	public static final String VER_CHKSUM = "chksum";
	public static final String VER_SAVEDATE = "savedate";
	public static final String VER_PROV = "provenance";
	public static final String VER_REF = "refs";
	public static final String VER_PROVREF = "provrefs";
	public static final String VER_TYPE = "type";
	public static final String VER_SIZE = "size";
	public static final String VER_RVRT = "revert";
	public static final String VER_META = "meta";
	public static final String VER_COPIED = "copied";
	//in 0.3.0, if missing assume no external IDs
	public static final String VER_EXT_IDS = "extids";
	
	// meta document key & value
	public static final String META_KEY = "k";
	public static final String META_VALUE = "v";

	// type fields
	public static final String TYPE_CHKSUM = "chksum";
	public static final String TYPE_SIZE = "size";
	public static final String TYPE_SUBDATA = "subdata";
	public static final String TYPE_TYPE = "type";

	// shock fields
	public static final String SHOCK_CHKSUM = "chksum";
	public static final String SHOCK_NODE = "node";
	public static final String SHOCK_VER = "ver";
	// since 0.2.0, if missing assume false
	public static final String SHOCK_SORTED = "sorted";
	
	// GridFS fields
	// since 0.2.0, if missing assume false
	public static final String GFS_SORTED = "sorted";
	
	// admin fields
	public static final String ADMIN_NAME = "user";
	
	// configuration fields, since 0.4.1
	public static final String CONFIG_KEY = "config";
	public static final String CONFIG_VALUE = "config";
	public static final String CONFIG_UPDATE = "inupdate";
	public static final String CONFIG_SCHEMA_VERSION = "schemaver";
	
	
}
