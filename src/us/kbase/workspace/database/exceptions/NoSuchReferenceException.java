package us.kbase.workspace.database.exceptions;

import us.kbase.workspace.database.ObjectIdentifier;

/** 
 * Thrown when an object does not have a reference to another object.
 * @author gaprice@lbl.gov
 *
 */
@SuppressWarnings("serial")
public class NoSuchReferenceException extends WorkspaceDBException {
	
	//TODO TEST unit tests
	
	private final int listPosition;
	private final int fromPosition;
	private final ObjectIdentifier start;
	private final ObjectIdentifier from;
	private final ObjectIdentifier to;
	
	/** Construct a NoSuchReferenceException.
	 * @param message a message to return with the exception. If message is null, a message is
	 * automatically generated from the other arguments.
	 * @param listPosition The position of the reference path that triggered this exception in a
	 * list of reference paths.
	 * @param fromPosition the position of the object in the reference path that does not have a
	 * reference to the next object.
	 * @param from the object in the reference path that does not have a reference to the next
	 * object.
	 * @param to the object in the reference path to which the from object does not have a
	 * reference.
	 */
	public NoSuchReferenceException(
			final String message,
			final int listPosition,
			final int fromPosition,
			final ObjectIdentifier start,
			final ObjectIdentifier from,
			final ObjectIdentifier to) {
		super(message != null ? message : String.format(
				"Reference path #%s starting with object %s %sin workspace %s, position %s: " +
				"Object %s %sin workspace %s does not contain a reference to object %s %sin " +
				"workspace %s",
				listPosition,
				start.getIdentifierString(),
				!start.getVersion().isPresent() ? "" : "version " + start.getVersion().get() + " ",
				start.getWorkspaceIdentifierString(),
				fromPosition,
				from.getIdentifierString(),
				!from.getVersion().isPresent()? "" : "version " + from.getVersion().get() + " ",
				from.getWorkspaceIdentifierString(),
				to.getIdentifierString(),
				!to.getVersion().isPresent() ? "" : "version " + to.getVersion().get() + " ",
				to.getWorkspaceIdentifierString()));
		this.listPosition = listPosition;
		this.fromPosition = fromPosition;
		this.start = start;
		this.from = from;
		this.to = to;
	}
	
	/** Get the position of the reference path that triggered this exception in a list of reference
	 * paths.
	 * @return the position of the reference path.
	 */
	public int getListPosition() {
		return listPosition;
	}

	/** Get the position of the object in the reference path that does not have a reference to the
	 * next object. 
	 * @return the position of the 'from' object in the reference path.
	 */
	public int getFromPosition() {
		return fromPosition;
	}

	/** Returns the object at the start of the reference path.
	 * @return the start object.
	 */
	public ObjectIdentifier getStartObject() {
		return start;
	}
	
	/** Get the object from which there is no reference to the 'to', or next, object in the
	 * reference path.
	 * @return the 'from' object.
	 */
	public ObjectIdentifier getFromObject() {
		return from;
	}

	/** Get the object in the reference path from which the 'from' object does not have a
	 * reference.
	 * @return the 'to' object.
	 */
	public ObjectIdentifier getToObject() {
		return to;
	}
}
