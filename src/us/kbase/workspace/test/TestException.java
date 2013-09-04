package us.kbase.workspace.test;

/** 
 * Thrown when an exception occurs while setting up tests.
 * @author gaprice@lbl.gov
 *
 */
public class TestException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public TestException() { super(); }
	public TestException(String message) { super(message); }
	public TestException(String message, Throwable cause) { super(message, cause); }
	public TestException(Throwable cause) { super(cause); }
}
