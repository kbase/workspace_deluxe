package us.kbase.workspace.database.mongo;

import java.util.Date;
import java.util.Map;

import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoObjectUserMeta extends MongoObjectMeta implements
		ObjectUserMetaData {
	
	final private Map<String, String> userMeta;
	
	public MongoObjectUserMeta(final int id, final String name,
			final String typeString, final Date createdDate, final int version,
			final WorkspaceUser creator, final ResolvedMongoWSID workspaceid,
			final String chksum, final int size,
			final Map<String, String> userMeta) {
		super(id, name, typeString, createdDate, version, creator, workspaceid,
				chksum, size);
		//no error checking for now, add if needed
		this.userMeta = userMeta;
	}

	//meta is mutable
	@Override
	public Map<String, String> getUserMetaData() {
		return userMeta;
	}
	
	@Override
	public String toString() {
		return "MongoObjectMeta [id=" + getObjectId() + ", name=" + 
				getObjectName() + ", type=" + getTypeString() + 
				", createdDate=" + getCreatedDate() + ", version="
				+ getVersion() + ", creator=" + getCreator() + ", workspaceId="
				+ getWorkspaceId() + ", chksum=" + getCheckSum() + 
				", userMeta=" + userMeta + "]";
	}

}
