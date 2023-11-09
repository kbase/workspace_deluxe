package us.kbase.workspace.database;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;

public class Util {

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

	/** Check if a string is null or whitespace only.
	 * @param s the string to test.
	 * @return true if the string is null or whitespace only, false otherwise.
	 */
	public static boolean isNullOrWhitespace(final String s) {
		return s == null || s.trim().isEmpty();
	}

	/** Throws a null pointer exception if an object is null.
	 * @param o the object to check.
	 * @param message the message for the exception.
	 */
	public static void nonNull(final Object o, final String message) {
		// TODO CODE replace with requireNonNull
		if (o == null) {
			throw new NullPointerException(message);
		}
	}

	/** Throws a null pointer exception if any elements in a collection are null.
	 * @param col the collection to check.
	 * @param message the exception message.
	 * @param <T> the type of the elements in the collection.
	 * @return the collection.
	 */
	public static <T> Collection<T> noNulls(final Collection<T> col, final String message) {
		for (final T item: col) {
			if (item == null) {
				throw new NullPointerException(message);
			}
		}
		return col;
	}

	/** Check that the provided collection is not null and contains no null or whitespace-only
	 * strings.
	 * @param strings the collection to check.
	 * @param name the name of the collection to use in any error messages.
	 */
	public static void checkNoNullsOrEmpties(final Collection<String> strings, final String name) {
		checkNotNull(strings, name);
		for (final String s: strings) {
			if (isNullOrWhitespace(s)) {
				throw new IllegalArgumentException(
						"Null or whitespace only string in collection " + name);
			}
		}
	}

        /** Check that a string is non-null and has at least one non-whitespace character.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
         * @param max maximum number of code points in the string.
	 * @return the trimmed string.
	 */
	public static String checkString(
			final String s,
			final String name,
			final int max) throws IllegalArgumentException {
                return checkString(s, name, max, false);
        }

        /** Check that a string is either null or that it has at least one non-whitespace character.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
         * @param optional whether or not the field can be null.
	 * @return the trimmed string.
	 */
        public static String checkString(
                final String s,
                final String name,
                final boolean optional) throws IllegalArgumentException {
                return checkString(s, name, -1, optional);
        }

        /** Check that a string is non-null and has at least one non-whitespace character.
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
	 * @return the trimmed string.
	 */
	public static String checkString(final String s, final String name)
			throws IllegalArgumentException {
		return checkString(s, name, -1, false);
	}

	/** Check that a string is non-null, has at least one non-whitespace character, and is below
	 * a specified length (not including surrounding whitespace).
	 * @param s the string to check.
	 * @param name the name of the string to use in any error messages.
	 * @param max the maximum number of code points in the string. If 0 or less, the length is not
	 * checked.
         * @param optional whether or not the field is optional; if the field is optional,
         *                 checkString will not throw an error for null or whitespace-only values.
	 * @return the trimmed string.
	 */
	public static String checkString(
			final String s,
			final String name,
			final int max,
                        final boolean optional) {
		if (isNullOrWhitespace(s)) {
                        if (optional) {
                                return null;
                        }
                        throw new IllegalArgumentException(name + " cannot be null or whitespace only");
                }
		if (max > 0 && codePoints(s.trim()) > max) {
			throw new IllegalArgumentException(
					name + " size greater than limit " + max);
		}
		return s.trim();
	}


	/** Return the number of code points in a string. Equivalent to
	 * {@link String#codePointCount(int, int)} with arguments of 0 and {@link String#length()}.
	 * @param s the string.
	 * @return the number of code points.
	 */
	public static int codePoints(final String s) {
		return s.codePointCount(0, s.length());
	}
}
