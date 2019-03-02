package us.kbase.workspace.kbase;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
	
	private String shockUrl;
	private String shockUser;
	private String backendType;
	private String typeDatabase;
	
	private static final String SHOCK = "shock";
	private static final String GFS = "gridFS";
	// settings fields
	public static final String SET_TYPE_DB = "type_db";
	public static final String SET_BACKEND = "backend";
	public static final String SET_SHOCK_USER = "shock_user";
	public static final String SET_SHOCK_LOC = "shock_location";

	@JsonCreator
	public Settings(@JsonProperty(SET_SHOCK_LOC) final String shockUrl,
			@JsonProperty(SET_SHOCK_USER) final String shockUser,
			@JsonProperty(SET_BACKEND) final String backendType,
			@JsonProperty(SET_TYPE_DB) final String typeDatabase) throws 
			CorruptWorkspaceDBException {
		this.shockUrl = shockUrl;
		this.shockUser = shockUser;
		if(!(backendType.equals(SHOCK) || backendType.equals(GFS))) {
			throw new CorruptWorkspaceDBException(
					"Illegal backend type: " + backendType);
		}
		this.backendType = backendType;
		this.typeDatabase = typeDatabase;
		if (typeDatabase == null || typeDatabase.isEmpty()) {
			throw new CorruptWorkspaceDBException("No type database provided");
		}
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
