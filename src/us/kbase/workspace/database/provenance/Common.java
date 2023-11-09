package us.kbase.workspace.database.provenance;

import static us.kbase.workspace.database.Util.checkNoNullsOrEmpties;
import static us.kbase.workspace.database.Util.isNullOrWhitespace;
import static us.kbase.workspace.database.Util.noNulls;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* This is an internal only class for shared code between the provenance classes.
 * Tests are all via testing the public provenance class APIs.
 */
class Common {

	private Common() {}

	static String processString(final String input) {
		return isNullOrWhitespace(input) ? null : input.strip();
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
