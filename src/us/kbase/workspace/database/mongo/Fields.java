package us.kbase.workspace.database.mongo;

public class Fields {
	
	public static final String FIELD_SEP = ".";
	
	public static final String MONGO_ID = "_id";
	
	//settings fields
	public static final String SET_TYPE_DB = "type_db";
	public static final String SET_BACKEND = "backend";
	public static final String SET_SHOCK_USER = "shock_user";
	public static final String SET_SHOCK_LOC = "shock_location";
	
	//workspace counter fields
	public static final String CNT_ID = "id";
	public static final String CNT_ID_VAL = "wscounter";
	public static final String CNT_NUM = "num";
	
	//workspace fields
	public static final String WS_OWNER = "owner";
	public static final String WS_ID = "id";
	public static final String WS_MODDATE = "moddate";
	public static final String WS_NAME = "name";
	public static final String WS_DEL = "del";
	public static final String WS_NUMPTR = "numpnter";
	public static final String WS_DESC = "desc";
	
	//workspace acl fields
	public static final String ACL_WSID = "id";
	public static final String ACL_PERM = "perm";
	public static final String ACL_USER = "user";
	
	//workspace pointer fields
	public static final String PTR_ID = "id";
	public static final String PTR_NAME = "name";
	public static final String PTR_WS_ID = "ws";
	public static final String PTR_MODDATE = "moddate";
	public static final String PTR_VCNT = "numver";
	public static final String PTR_VERS = "vers";
	public static final String PTR_DEL = "del";
	public static final String PTR_HIDE = "hide";
	
	//workspace version fields (inside the PTR_VERS field)
	public static final String PTR_VER_VER = "ver";
	public static final String PTR_VER_CREATEBY = "createdby";
	public static final String PTR_VER_CHKSUM = "chksum";
	public static final String PTR_VER_CREATEDATE = "createdate";
	public static final String PTR_VER_PROV = "provenance";
	public static final String PTR_VER_REF = "reffedby";
	public static final String PTR_VER_TYPE = "type";
	public static final String PTR_VER_SIZE = "size";
	public static final String PTR_VER_RVRT = "revert";
	public static final String PTR_VER_UUID = "legacyUUID";
	public static final String PTR_VER_META = "meta";
	//meta document key & value
	public static final String PTR_VER_META_KEY = "k";
	public static final String PTR_VER_META_VALUE = "v";
	
	//type fields
	public static final String TYPE_CHKSUM = "chksum";
	public static final String TYPE_SIZE = "size";
	public static final String TYPE_SUBDATA = "subdata";
	public static final String TYPE_VER = "minver";
	public static final String TYPE_WS = "ws";
	
	//shock fields
	public static final String SHOCK_CHKSUM = "chksum";
	public static final String SHOCK_NODE = "node";
	public static final String SHOCK_VER = "ver";

}
