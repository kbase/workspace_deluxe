package us.kbase.shock.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoFileException;

public class ShockNodeResponse {
	
	private ShockNodeResponse(){}
	
	//per Jared, the error field will either be null or a list with one error
	// string.
	private List<String> error;
	@JsonProperty("data")
	private ShockNode data;
	private int status;
	
	public String getError() {
		return error.get(0);
	}
	
	public boolean hasError() {
		return error != null;
	}
	
	@JsonIgnore
	public ShockNode getShockNode() throws ShockHttpException {
		if (error != null) {
			if (status == 401) {
				throw new ShockAuthorizationException(status, getError());
			} else if (status == 400 && getError().equals("Node has no file")) {
				throw new ShockNoFileException(status, getError());
			} else {
				throw new ShockHttpException(status, getError());
			}
		}
		return data;
	}
	
	public int getStatus() {
		return status;
	}

	@Override
	public String toString() {
		return "ShockNodeResponse [error=" + error.get(0) + ", data=" + data
				+ ", status=" + status + "]";
	}
}
