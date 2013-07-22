package us.kbase.shock.client;

import java.util.List;

import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoFileException;

public abstract class ShockResponse {
	
	protected ShockResponse(){}
	
	//per Jared, the error field will either be null or a list with one error
	// string.
	private List<String> error;
	private ShockData data;
	private int status;
	
	public String getError() {
		return error.get(0);
	}
	
	public boolean hasError() {
		return error != null;
	}
	
	public abstract ShockData getShockData() throws ShockHttpException;
	
	protected void checkErrors() throws ShockHttpException {
		if (error != null) {
			if (status == 401) {
				throw new ShockAuthorizationException(status, getError());
			} else if (status == 400 && getError().equals("Node has no file")) {
				throw new ShockNoFileException(status, getError());
			} else {
				throw new ShockHttpException(status, getError());
			}
		}
	}
	
	public int getStatus() {
		return status;
	}

	@Override
	public String toString() {
		return getClass().getName() + " [error=" + error.get(0) + 
				", data=" + data + ", status=" + status + "]";
	}
}
