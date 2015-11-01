package us.kbase.workspace.kbase;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.mongo.Fields;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
	
	private String shockUrl;
	private String shockUser;
	private String backendType;
	private String typeDatabase;
	
	private static final String SHOCK = "shock";
	private static final String GFS = "gridFS";

	@JsonCreator
	private Settings(@JsonProperty(Fields.SET_SHOCK_LOC) final String shockUrl,
			@JsonProperty(Fields.SET_SHOCK_USER) final String shockUser,
			@JsonProperty(Fields.SET_BACKEND) final String backendType,
			@JsonProperty(Fields.SET_TYPE_DB) final String typeDatabase) throws 
			CorruptWorkspaceDBException {
		this.shockUrl = shockUrl;
		this.shockUser = shockUser;
		if(!(backendType.equals(SHOCK) || backendType.equals(GFS))) {
			throw new CorruptWorkspaceDBException(
					"Illegal backend type: " + backendType);
		}
		this.backendType = backendType;
		this.typeDatabase = typeDatabase;
	}
	
	public String getShockUrl() {
		return shockUrl;
	}
	public String getShockUser() {
		return shockUser;
	}
	
	public boolean isShockBackend() {
		return backendType.equals(SHOCK);
	}
	
	public boolean isGridFSBackend() {
		return backendType.equals(GFS);
	}
	
	public String getTypeDatabase() {
		return typeDatabase;
	}

	@Override
	public String toString() {
		return "Settings [shockUrl=" + shockUrl + ", shockUser=" + shockUser
				+ ", backendType=" + backendType + ", typeDatabase="
				+ typeDatabase + "]";
	}

}
