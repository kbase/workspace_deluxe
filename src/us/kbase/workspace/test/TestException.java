package us.kbase.workspace.test;

/** 
 * Thrown when an exception occurs while setting up tests.
 * @author gaprice@lbl.gov
 *
 */
public class TestException extends RuntimeException {
	//TODO replace with version in java common
	private static final long serialVersionUID = 1L;
	
	public TestException() { super(); }
	public TestException(String message) { super(message); }
	public TestException(String message, Throwable cause) { super(message, cause); }
	public TestException(Throwable cause) { super(cause); }
}
