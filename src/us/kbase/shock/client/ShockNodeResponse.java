package us.kbase.shock.client;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;

public class ShockNodeResponse {
	
	private static final int STAT_AUTH = 401;
	
	private ShockNodeResponse(){}
	
	private List<String> error;
	private ShockNode data;
	private int status;
	
	public List<String> getError() {
		return error;
	}
	
	public boolean hasError() {
		return error != null;
	}
	
	public ShockNode getData() throws ShockHttpException {
		if (error != null) {
			String err = StringUtils.join(error, ", ");
			if (status == STAT_AUTH) {
				throw new ShockAuthorizationException(err);
			} else {
				throw new ShockHttpException(status, err);
			}
		}
		return data;
	}
	
	public int getStatus() {
		return status;
	}
}
