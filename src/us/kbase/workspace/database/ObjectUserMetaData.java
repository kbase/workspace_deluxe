package us.kbase.workspace.database;

import java.util.Map;

public interface ObjectUserMetaData extends ObjectMetaData {
	
	public Map<String, String> getUserMetaData();
}
