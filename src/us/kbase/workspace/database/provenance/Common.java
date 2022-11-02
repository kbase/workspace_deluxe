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

/* This is an internal only class for shared code between the provenance classes.
 * Tests are all via testing the public provenance class APIs.
 */
class Common {

	static final Pattern VALID_PID_REGEX = Pattern.compile("^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$");
	static final String VALID_PID_REGEX_STRING = VALID_PID_REGEX.toString();

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
	 * Checks that a PID string is either null, or has at least one non-whitespace
	 * character and conforms to the specified regular expression.
	 *
	 * @param putativePid the string to check.
	 * @param name        the name of the string to use in any error messages.
	 * @param optional    whether or not the field is optional. If true, null is a valid value for the PID.
	 * @return the trimmed PID.
	 */
	static String checkPid(final String putativePid, final String name, final boolean optional)
			throws IllegalArgumentException {
		final String pid = checkString(putativePid, name, optional);
		if (pid == null) {
			return null;
		}
		final Matcher m = VALID_PID_REGEX.matcher(pid);
		if (m.find()) {
			return m.replaceAll("$1:$2");
		}
		throw new IllegalArgumentException(String.format(
				"Illegal ID format for %s: \"%s\"\nPIDs should match the pattern \"%s\"",
				name, putativePid, VALID_PID_REGEX_STRING));
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
		 * 2) making a copy of the list so mutating the old list doesn't mutate the new one.
		 *
		 * Note the individual items of the list can still be mutated, since they aren't copied.
		 */
		return Collections.unmodifiableList(new ArrayList<>(list));
	}

	static <T> List<T> getList(final List<T> list) {
		return list == null ? Collections.emptyList() : list;
	}
}
