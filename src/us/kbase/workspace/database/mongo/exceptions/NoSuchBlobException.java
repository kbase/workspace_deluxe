package us.kbase.workspace.database.mongo.exceptions;

import static java.util.Objects.requireNonNull;

import us.kbase.typedobj.core.MD5;

/** 
 * Thrown when attempting to retrieve a blob that doesn't exist.
 * @author gaprice@lbl.gov
 *
 */
public class NoSuchBlobException extends BlobStoreException {

	private static final long serialVersionUID = 1L;
	private final MD5 md5;
	
	public NoSuchBlobException(final String message, final MD5 md5) {
		super(message);
		this.md5 = requireNonNull(md5, "md5");
	}
	
	public MD5 getMD5() {
		return md5;
	}
}
