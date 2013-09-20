package us.kbase.common.service;

/** 
 * A KBase JSONRPC exception raised by a client when the server responds with an error or the server response could not be handled.
 * @author gaprice@lbl.gov
 *
 */
public class ServerException extends JsonClientException {

	private static final long serialVersionUID = 1L;
	private final int code;
	private final String name;
	private final String data;
	
	public ServerException(String message, int code, String name, String data) {
		super(message);
		this.code = code;
		this.name = name;
		this.data = data;
	}
	public ServerException(String message, int code, String name) {
		this(message, code, name, null);
	}
	
	public int getCode() {
		return code;
	}
	
	public String getName() {
		return name;
	}
	
	public String getData() {
		return data;
	}
}
