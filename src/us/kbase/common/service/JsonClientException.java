package us.kbase.common.service;

/** 
 * The base class of all KBase JSONRPC exceptions raised by a client.
 * @author gaprice@lbl.gov
 *
 */
public class JsonClientException extends Exception {

	private static final long serialVersionUID = 1L;

	
	public JsonClientException(String message) {super(message);}
	public JsonClientException(String message, Throwable cause) {super(message, cause);}
}
