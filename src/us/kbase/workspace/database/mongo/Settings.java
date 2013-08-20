package us.kbase.workspace.database.mongo;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
	
	private String shockUrl;
	private String shockUser;
	private String backendType;
	
	private static final String SHOCK = "shock";
	private static final String GFS = "gridFS";

	@JsonCreator
	private Settings(@JsonProperty("shock_location") String shockUrl,
			@JsonProperty("shock_user") String shockUser,
			@JsonProperty("backend") String backendType) throws 
			CorruptWorkspaceDBException {
		this.shockUrl = shockUrl;
		this.shockUser = shockUser;
		if(!(backendType.equals(SHOCK) || backendType.equals(GFS))) {
			throw new CorruptWorkspaceDBException(
					"Illegal backend type: " + backendType);
		}
		this.backendType = backendType;
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

	@Override
	public String toString() {
		return this.getClass().getName() +
				" [shockUrl=" + shockUrl + ", shockUser=" + shockUser
				+ ", backendType=" + backendType + "]";
	}

}
