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
	//TODO DBUPDATE remove this field. Deleting versions is out, just delete the entire object.
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
	// the full type string, e.g. Module.Type-3.4
	// TODO CODE kept to allow for rollbacks, but unused at this point. remove in future
	public static final String VER_TYPE_FULL = "type";
	// the type name only as a type string, e.g. Module.Type
	public static final String VER_TYPE_NAME = "tyname";
	// the major version of the type, e.g. 3
	public static final String VER_TYPE_MAJOR_VERSION = "tymaj";
	// the minor version of the type, e.g. 4
	public static final String VER_TYPE_MINOR_VERSION = "tymin";
	public static final String VER_SIZE = "size";
	public static final String VER_RVRT = "revert";
	public static final String VER_META = "meta";
	public static final String VER_COPIED = "copied";
	//in 0.3.0, if missing assume no external IDs
	public static final String VER_EXT_IDS = "extids";
	
	// meta document key & value
	public static final String META_KEY = "k";
	public static final String META_VALUE = "v";

	//provenance fields
	public static final String PROV_USER = "user";
	public static final String PROV_WS_ID = "wsid";
	public static final String PROV_DATE = "date";
	public static final String PROV_ACTIONS = "actions";
	//provenance action fields
	public static final String PROV_ACTION_TIME = "time";
	public static final String PROV_ACTION_CALLER = "caller";
	public static final String PROV_ACTION_SERVICE = "service";
	public static final String PROV_ACTION_SERVICE_VER = "serviceVersion";
	public static final String PROV_ACTION_METHOD = "method";
	public static final String PROV_ACTION_METHOD_PARAMS = "methodParameters";
	public static final String PROV_ACTION_SCRIPT = "script";
	public static final String PROV_ACTION_SCRIPT_VER = "scriptVersion";
	public static final String PROV_ACTION_COMMAND_LINE = "commandLine";
	public static final String PROV_ACTION_WS_OBJS = "wsobjs";
	public static final String PROV_ACTION_INCOMING_ARGS = "incomingArgs";
	public static final String PROV_ACTION_OUTGOING_ARGS = "outgoingArgs";
	public static final String PROV_ACTION_EXTERNAL_DATA = "externalData";
	public static final String PROV_ACTION_SUB_ACTIONS = "subActions";
	public static final String PROV_ACTION_CUSTOM = "custom";
	public static final String PROV_ACTION_DESCRIPTION = "description";
	//external data fields
	public static final String PROV_EXTDATA_RESOURCE_NAME = "resourceName";
	public static final String PROV_EXTDATA_RESOURCE_URL = "resourceUrl";
	public static final String PROV_EXTDATA_RESOURCE_VER = "resourceVersion";
	public static final String PROV_EXTDATA_RESOURCE_DATE = "resourceReleaseDate";
	public static final String PROV_EXTDATA_DATA_URL = "dataUrl";
	public static final String PROV_EXTDATA_DATA_ID = "dataId";
	public static final String PROV_EXTDATA_DESCRIPTION = "description";
	//subaction fields
	public static final String PROV_SUBACTION_NAME = "name";
	public static final String PROV_SUBACTION_VER = "ver";
	public static final String PROV_SUBACTION_CODE_URL = "codeUrl";
	public static final String PROV_SUBACTION_COMMIT = "commit";
	public static final String PROV_SUBACTION_ENDPOINT_URL = "endpointUrl";
	
	// GridFS fields
	// since 0.2.0, if missing assume false
	public static final String GFS_SORTED = "sorted";
	
	// s3 fields
	// since 0.10.1.
	public static final String S3_CHKSUM = "chksum";
	public static final String S3_KEY = "key";
	// may have older data ported in that isn't sorted
	public static final String S3_SORTED = "sorted";
	
	// admin fields
	public static final String ADMIN_NAME = "user";
	
	// schema configuration fields, since 0.4.1
	// this was an unfortunate choice of name
	public static final String SCHEMA_CONFIG_KEY = "config";
	public static final String SCHEMA_CONFIG_VALUE = "config";
	public static final String SCHEMA_CONFIG_UPDATE = "inupdate";
	public static final String SCHEMA_CONFIG_VERSION = "schemaver";
	
	// dynamic configuration keys, since 0.13.0
	public static final String DYNAMIC_CONFIG_KEY = "key";
	public static final String DYNAMIC_CONFIG_VALUE = "value";
}
