package us.kbase.workspace.database.provenance;

import static us.kbase.workspace.database.Util.checkNoNullsOrEmpties;
import static us.kbase.workspace.database.Util.isNullOrWhitespace;
import static us.kbase.workspace.database.Util.noNulls;
import static us.kbase.workspace.database.Util.checkString;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Objects;

/* This is an internal only class for shared code between the provenance classes.
 * Tests are all via testing the public provenance class APIs.
 */
class Common {

	static final Pattern VALID_PID_REGEX = Pattern.compile("^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$");
	static final String VALID_PID_REPLACEMENT = "$1:$2";

	static final String DATACITE = "DataCite";
	static final String CROSSREF = "Crossref";
	static final String CREDIT = "CRediT";

	private Common() {}

	static String processString(final String input) {
		return isNullOrWhitespace(input) ? null : input.trim();
	}

	static URL processURL(final String url, final String name) {
		return isNullOrWhitespace(url) ? null : checkURL(url, name);
	}

	static URL processURL(final URL url, final String name) {
		return url == null ? null : checkURL(url, name);
	}

	static List<String> processSimpleStringList(final List<String> list, final String name) {
		if (list == null || list.isEmpty()) {
			return null;
		} else {
			checkNoNullsOrEmpties(list, name);
			return Common.immutable(list);
		}
	}

	static <T> List<T> processSimpleList(final List<T> list, final String name) {
		if (list == null || list.isEmpty()) {
			return null;
		} else {
			noNulls(list, "Null item in " + name);
			return Common.immutable(list);
		}
	}

	/**
	 * Returns a new immutable list with nulls and duplicates removed
	 *
	 * @param <T>
	 * @param list
	 *                list of items to deduplicate
	 * @return immutable deduplicated list
	 */
	static <T> List<T> dedupeSimpleList(final List<T> list) {
		if (list == null) {
			return list;
		}

		final List<T> dedupedList = list.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());

		return Common.immutable(dedupedList);
	}

	/**
	 * Trims leading and trailing whitespace, converts empty strings to null, and
	 * checks that a string is either null or has at least one non-whitespace
	 * character, and conforms to the specified regular expression.
	 * If optional is true, null is a valid output value; if false, null will throw
	 * an error.
	 * If replace is not null, it is used for a replaceAll operation, and the
	 * resulting string returned. Otherwise, the trimmed string is returned.
	 *
	 * @param stringToCheck
	 *                the string to check.
	 * @param pattern
	 *                the pattern to validate against.
	 * @param replace
	 *                if non-null, the pattern to use for the replaceAll operation.
	 * @param name
	 *                the name of the string to use in any error messages.
	 * @param optional
	 *                whether or not the field is optional. If false, null and
	 *                empty or whitespace-only input strings will throw an error.
	 *
	 * @return the trimmed field, or null if the input string was null or
	 *         whitespace.
	 */
	static String checkAgainstRegex(final String stringToCheck, final Pattern pattern, final String replace,
			final String name, final boolean optional)
			throws IllegalArgumentException {
		final String checkedString = checkString(stringToCheck, name, optional);
		if (checkedString == null) {
			return null;
		}
		final Matcher m = pattern.matcher(checkedString);
		if (m.find()) {
			if (replace != null) {
				return m.replaceAll(replace);
			}
			return checkedString;
		}
		throw new IllegalArgumentException(String.format(
				"Illegal format for %s: \"%s\"\nIt should match the pattern \"%s\"",
				name, stringToCheck, pattern.toString()));
	}

	/**
	 * Trims leading and trailing whitespace, converts empty strings to null, and
	 * checks that a string is either null or has at least one non-whitespace
	 * character, and conforms to the regular expression VALID_PID_REGEX.
	 * If optional is true, null is a valid output value; if false, null will throw
	 * an error.
	 *
	 * @param putativePid
	 *                the putative PID string.
	 * @param name
	 *                the name of the string to use in any error messages.
	 * @param optional
	 *                whether or not the field is optional. If false, null and
	 *                empty or whitespace-only input strings will throw an error.
	 * @return the trimmed field, or null if the input string was null or
	 *         whitespace.
	 */
	static String checkPid(final String putativePid, final String name, final boolean optional)
			throws IllegalArgumentException {
		return checkAgainstRegex(putativePid, VALID_PID_REGEX, VALID_PID_REPLACEMENT, name, optional);
	}

	private static URL checkURL(final String putativeURL, final String name) {
		final URL url;
		try {
			url = new URL(putativeURL);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(String.format(
					"Illegal %s url '%s': %s", name, putativeURL, e.getLocalizedMessage()), e);
		}
		return checkURL(url, name);
	}

	private static URL checkURL(final URL url, final String name) {
		try {
			url.toURI();
			return url;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(String.format(
					"Illegal %s url '%s': %s", name, url, e.getLocalizedMessage()), e);
		}
	}

	static <T> List<T> immutable(final List<T> list) {
		/*
		 * makes a list immutable by
		 * 1) making changes to the returned list impossible
		 * 2) making a copy of the list so mutating the old list doesn't mutate the new
		 * one.
		 *
		 * Note the individual items of the list can still be mutated, since they aren't
		 * copied.
		 */
		return Collections.unmodifiableList(new ArrayList<>(list));
	}

	static <T> List<T> getList(final List<T> list) {
		return list == null ? Collections.emptyList() : list;
	}
}
