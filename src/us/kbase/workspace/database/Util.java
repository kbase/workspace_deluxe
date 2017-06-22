package us.kbase.workspace.database;

import java.util.Collection;

public class Util {
	
	//TODO TEST unit tests 

	/** Check that one and only one of a name or id is provided.
	 * @param name the name.
	 * @param id the id.
	 * @param type the type of the name or id.
	 */
	public static void xorNameId(final String name, final Long id, final String type) {
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of %s name (was: %s) or id (was: %s)",
					type, name, id));
		}
	}
	
	/** Throws a null pointer exception if an object is null.
	 * @param o the object to check.
	 * @param message the message for the exception.
	 */
	public static void nonNull(final Object o, final String message) {
		if (o == null) {
			throw new NullPointerException(message);
		}
	}
	
	/** Throws a null pointer exception if any elements in a collection are null.
	 * @param col the collection to check.
	 * @param message the exception message.
	 * @param <T> the type of the elements in the collection.
	 */
	public static <T> void noNulls(final Collection<T> col, final String message) {
		for (final T item: col) {
			if (item == null) {
				throw new NullPointerException(message);
			}
		}
	}

}
