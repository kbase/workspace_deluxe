package us.kbase.workspace.database.exceptions;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.Optional;

/** 
 * A class that represents an object or an error that occurred attempting to create or retrieve
 * the object.
 */
public class ErrorOr<T> {

	// TODO ERROR may want to support including custom error fields? Different errors = different fields
	// TODO LOMBOK
	// TODO CODE return this from bulk methods rather than object / null 
	
	private final ErrorType error;
	private final T object;
	
	
	/** Create an object containing an error.
	 * @param error the error.
	 */
	public ErrorOr(final ErrorType error) {
		this.object = null;
		this.error = requireNonNull(error, "error");
	}
	
	/** Create an object containing a result object. Null is acceptable, but consider using
	 * {@link Optional} instead.
	 * @param object the object.
	 */
	public ErrorOr(final T object) {
		this.object = object;
		this.error = null;
	}
	
	/** Returns true if this instance contains an error, false if an object.
	 * @return true if this instance contains an error.
	 */
	public boolean isError() {
		return error != null;
	}
	
	/** Get the error, if any.
	 * @return the error.
	 * @throws NoSuchElementException if this instance does not contain an error.
	 */
	public ErrorType getError() {
		if (error == null) {
			throw new NoSuchElementException("error");
		}
		return error;
	}
	
	/** Get the object, if any. The object may be null.
	 * @return the object.
	 * @throws NoSuchElementException if this instance does not contain an error.
	 */
	public T getObject() {
		if (error != null) {
			throw new NoSuchElementException("object");
		}
		return object;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((error == null) ? 0 : error.hashCode());
		result = prime * result + ((object == null) ? 0 : object.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ErrorOr<?> other = (ErrorOr<?>) obj;
		if (error != other.error)
			return false;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}
}

