package us.kbase.workspace.database.provenance;

import static us.kbase.workspace.database.Util.isNullOrEmpty;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/* This is an internal only class for shared code between the provenance classes.
 * Tests are all via testing the public provenance class APIs.
 */
class Common {

	static String processString(final String input) {
		return isNullOrEmpty(input) ? null : input.trim();
	}

	static URL processURL(final String url) {
		return isNullOrEmpty(url) ? null : checkURL(url);
	}

	static URL processURL(final URL url) {
		return url == null ? null : checkURL(url);
	}
	
	static URL checkURL(final String putativeURL) {
		final URL url;
		try {
			url = new URL(putativeURL);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(String.format(
					"Illegal url '%s': %s", putativeURL, e.getLocalizedMessage()), e);
		}
		return checkURL(url);
	}
	
	static URL checkURL(final URL url) {
		try {
			url.toURI();
			return url;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(String.format(
					"Illegal url '%s': %s", url, e.getLocalizedMessage()), e);
		}
	}
}
