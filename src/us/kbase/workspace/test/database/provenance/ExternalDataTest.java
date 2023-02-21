package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.inst;

import java.net.URL;
import java.time.Instant;
import java.util.Optional;

import static us.kbase.common.test.TestCommon.ES;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.ExternalData;

@Category(us.kbase.common.test.ProvenanceTests.class)
public class ExternalDataTest {

	private static final Optional<URL> EU = Optional.empty();
	private static final Optional<Instant> EI = Optional.empty();

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ExternalData.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		// build with a couple of fields populated since at least one field must be populated
		final ExternalData ed1 = ExternalData.getBuilder().withDataID("foobar").build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foobar")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		final ExternalData ed2 = ExternalData.getBuilder()
				.withResourceURL("https://foo.com").build();

		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL", ed2.getDataURL(), is(EU));
		assertThat("incorrect desc", ed2.getDescription(), is(ES));
		assertThat("incorrect name", ed2.getResourceName(), is(ES));
		assertThat("incorrect date", ed2.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed2.getResourceURL(), is(opt(new URL("https://foo.com"))));
		assertThat("incorrect version", ed2.getResourceVersion(), is(ES));
	}

	@Test
	public void buildMinimalOneFieldAtATime() throws Exception {
		// since at least one field must be populated to build, but that can be any field,
		// we test building with each field individually.
		final ExternalData.Builder b = ExternalData.getBuilder().withDataID("foobar");
		final ExternalData ed1 = b.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foobar")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		b.withDataID(null).withDataURL("https://lolcats.com");
		final ExternalData ed2 = b.build();
		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL",
				ed2.getDataURL(), is(opt(new URL("https://lolcats.com"))));

		b.withDataURL("").withDescription("my desc");
		final ExternalData ed3 = b.build();
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed3.getDescription(), is(opt("my desc")));

		b.withDescription(null).withResourceName("   expendable human resource ID #78698615431");
		final ExternalData ed4 = b.build();
		assertThat("incorrect desc", ed4.getDescription(), is(ES));
		assertThat("incorrect name",
				ed4.getResourceName(), is(opt("expendable human resource ID #78698615431")));

		b.withResourceName(null).withResourceReleaseDate(inst(70000));
		final ExternalData ed5 = b.build();
		assertThat("incorrect name", ed5.getResourceName(), is(ES));
		assertThat("incorrect date", ed5.getResourceReleaseDate(), is(opt(inst(70000))));

		b.withResourceReleaseDate(null).withResourceURL("https://yay.com");
		final ExternalData ed6 = b.build();
		assertThat("incorrect date", ed6.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed6.getResourceURL(), is(opt(new URL("https://yay.com"))));

		b.withResourceURL("").withResourceVersion(" v0.0.1");
		final ExternalData ed7 = b.build();
		assertThat("incorrect resource URL", ed7.getResourceURL(), is(EU));
		assertThat("incorrect version", ed7.getResourceVersion(), is(opt("v0.0.1")));
	}

	@Test
	public void buildMinimalWithStringURLsWithNulls() throws Exception {
		// build with a couple of fields populated since at least one field must be populated
		final ExternalData ed1 = ExternalData.getBuilder()
				.withDataID("foobaz")
				.withDataURL((String) null)
				.withDescription(null)
				.withResourceName(null)
				.withResourceReleaseDate(null)
				.withResourceURL((String) null)
				.withResourceVersion(null)
				.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foobaz")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		final ExternalData ed2 = ExternalData.getBuilder()
				.withDataID(null)
				.withDataURL((String) null)
				.withDescription(null)
				.withResourceName(null)
				.withResourceReleaseDate(null)
				.withResourceURL("https://foo2.com")
				.withResourceVersion(null)
				.build();

		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL", ed2.getDataURL(), is(EU));
		assertThat("incorrect desc", ed2.getDescription(), is(ES));
		assertThat("incorrect name", ed2.getResourceName(), is(ES));
		assertThat("incorrect date", ed2.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed2.getResourceURL(), is(opt(new URL("https://foo2.com"))));
		assertThat("incorrect version", ed2.getResourceVersion(), is(ES));
	}

	@Test
	public void buildMinimalWithStringURLsWithEmptyStrings() throws Exception {
		// build with a couple of fields populated since at least one field must be populated
		final String es = "     \t      ";
		final ExternalData ed1 = ExternalData.getBuilder()
				.withDataID("foobat")
				.withDataURL(es)
				.withDescription(es)
				.withResourceName(es)
				.withResourceURL(es)
				.withResourceVersion(es)
				.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foobat")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		final ExternalData ed2 = ExternalData.getBuilder()
				.withDataID(es)
				.withDataURL(es)
				.withDescription(es)
				.withResourceName(es)
				.withResourceURL("https://foo3.com")
				.withResourceVersion(es)
				.build();

		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL", ed2.getDataURL(), is(EU));
		assertThat("incorrect desc", ed2.getDescription(), is(ES));
		assertThat("incorrect name", ed2.getResourceName(), is(ES));
		assertThat("incorrect date", ed2.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed2.getResourceURL(), is(opt(new URL("https://foo3.com"))));
		assertThat("incorrect version", ed2.getResourceVersion(), is(ES));
	}

	@Test
	public void buildMinimalWithClassURLsWithNulls() throws Exception {
		final ExternalData ed1 = ExternalData.getBuilder()
				.withDataID("foobang")
				.withDataURL((URL) null)
				.withResourceURL((URL) null)
				.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foobang")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));
	}

	@Test
	public void buildMaximalWithStringURLs() throws Exception {
		// tests stripping of values
		final ExternalData ed = ExternalData.getBuilder()
				.withDataID("  \t   foo    ")
				.withDataURL("     https://foo.com  \t    ")
				.withDescription("   \tdesc     ")
				.withResourceName("    res name \t ")
				.withResourceReleaseDate(inst(30000))
				.withResourceURL("     ftp://bar.com  \t    ")
				.withResourceVersion("   -99    \t ")
				.build();
		assertThat("incorrect data ID", ed.getDataID(), is(opt("foo")));
		assertThat("incorrect data URL", ed.getDataURL(), is(opt(new URL("https://foo.com"))));
		assertThat("incorrect desc", ed.getDescription(), is(opt("desc")));
		assertThat("incorrect name", ed.getResourceName(), is(opt("res name")));
		assertThat("incorrect date", ed.getResourceReleaseDate(), is(opt(inst(30000))));
		assertThat("incorrect resource URL",
				ed.getResourceURL(), is(opt(new URL("ftp://bar.com"))));
		assertThat("incorrect version", ed.getResourceVersion(), is(opt("-99")));
	}

	@Test
	public void buildWithClassURLs() throws Exception {
		final ExternalData ed = ExternalData.getBuilder()
				.withDataURL(new URL("     https://foo.com  \t    "))
				.withResourceURL(new URL("     ftp://bar.com  \t    "))
				.build();
		assertThat("incorrect data ID", ed.getDataID(), is(ES));
		assertThat("incorrect data URL", ed.getDataURL(), is(opt(new URL("https://foo.com"))));
		assertThat("incorrect desc", ed.getDescription(), is(ES));
		assertThat("incorrect name", ed.getResourceName(), is(ES));
		assertThat("incorrect date", ed.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed.getResourceURL(), is(opt(new URL("ftp://bar.com"))));
		assertThat("incorrect version", ed.getResourceVersion(), is(ES));
	}

	@Test
	public void buildAndOverwriteAllWithEmptyStrings() throws Exception {
		final ExternalData ed1 = ExternalData.getBuilder()
				.withDataID("  \t   foo    ")
				.withDataURL("     https://foo.com  \t    ")
				.withDataURL("   \t   ")
				.withDescription("   \tdesc     ")
				.withDescription("   \t    ")
				.withResourceName("    res name \t ")
				.withResourceName("   \t   ")
				.withResourceReleaseDate(inst(30000))
				.withResourceURL("     ftp://bar.com  \t    ")
				.withResourceURL("   \t    ")
				.withResourceVersion("   -99    \t ")
				.withResourceVersion("      ")
				.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foo")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(opt(inst(30000))));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		final ExternalData ed2 = ExternalData.getBuilder()
				.withDataID("  \t   foo    ")
				.withDataID("    \t   ")
				.withDescription("bar")
				.build();
		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL", ed2.getDataURL(), is(EU));
		assertThat("incorrect desc", ed2.getDescription(), is(opt("bar")));
		assertThat("incorrect name", ed2.getResourceName(), is(ES));
		assertThat("incorrect date", ed2.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed2.getResourceURL(), is(EU));
		assertThat("incorrect version", ed2.getResourceVersion(), is(ES));
	}

	@Test
	public void buildAndOverwriteAllWithNulls() throws Exception {
		final ExternalData ed1 = ExternalData.getBuilder()
				.withDataID("  \t   foo    ")
				.withDataURL("     https://foo.com  \t    ")
				.withDataURL((URL) null)
				.withDescription("   \tdesc     ")
				.withDescription(null)
				.withResourceName("    res name \t ")
				.withResourceName(null)
				.withResourceReleaseDate(inst(30000))
				.withResourceReleaseDate(null)
				.withResourceURL("     ftp://bar.com  \t    ")
				.withResourceURL((URL) null)
				.withResourceVersion("   -99    \t ")
				.withResourceVersion(null)
				.build();
		assertThat("incorrect data ID", ed1.getDataID(), is(opt("foo")));
		assertThat("incorrect data URL", ed1.getDataURL(), is(EU));
		assertThat("incorrect desc", ed1.getDescription(), is(ES));
		assertThat("incorrect name", ed1.getResourceName(), is(ES));
		assertThat("incorrect date", ed1.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed1.getResourceURL(), is(EU));
		assertThat("incorrect version", ed1.getResourceVersion(), is(ES));

		final ExternalData ed2 = ExternalData.getBuilder()
				.withDataID("  \t   foo    ")
				.withDataID(null)
				.withDescription("bar")
				.build();
		assertThat("incorrect data ID", ed2.getDataID(), is(ES));
		assertThat("incorrect data URL", ed2.getDataURL(), is(EU));
		assertThat("incorrect desc", ed2.getDescription(), is(opt("bar")));
		assertThat("incorrect name", ed2.getResourceName(), is(ES));
		assertThat("incorrect date", ed2.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL", ed2.getResourceURL(), is(EU));
		assertThat("incorrect version", ed2.getResourceVersion(), is(ES));
	}

	@Test
	public void buildAndOverwriteStringURLs() throws Exception {
		final ExternalData ed = ExternalData.getBuilder()
				.withDataURL("mailto://rats.thing")
				.withDataURL(new URL("     https://foo2.com  \t    "))
				.withResourceURL("file:///home/imtheprez/fsbcontacts.xls")
				.withResourceURL(new URL("     ftp://bar2.com  \t    "))
				.build();
		assertThat("incorrect data ID", ed.getDataID(), is(ES));
		assertThat("incorrect data URL", ed.getDataURL(), is(opt(new URL("https://foo2.com"))));
		assertThat("incorrect desc", ed.getDescription(), is(ES));
		assertThat("incorrect name", ed.getResourceName(), is(ES));
		assertThat("incorrect date", ed.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed.getResourceURL(), is(opt(new URL("ftp://bar2.com"))));
		assertThat("incorrect version", ed.getResourceVersion(), is(ES));
	}

	@Test
	public void buildAndOverwriteClassURLs() throws Exception {
		final ExternalData ed = ExternalData.getBuilder()
				.withDataURL(new URL("     https://foo2.com  \t    "))
				.withDataURL("mailto://rats.thing")
				.withResourceURL(new URL("     ftp://bar2.com  \t    "))
				.withResourceURL("file:/home/chrisrock/ow.mp4")
				.build();
		assertThat("incorrect data ID", ed.getDataID(), is(ES));
		assertThat("incorrect data URL", ed.getDataURL(), is(opt(new URL("mailto://rats.thing"))));
		assertThat("incorrect desc", ed.getDescription(), is(ES));
		assertThat("incorrect name", ed.getResourceName(), is(ES));
		assertThat("incorrect date", ed.getResourceReleaseDate(), is(EI));
		assertThat("incorrect resource URL",
				ed.getResourceURL(), is(opt(new URL("file:/home/chrisrock/ow.mp4"))));
		assertThat("incorrect version", ed.getResourceVersion(), is(ES));
	}

	@Test
	public void withDataURLFail() throws Exception {
		// string input
		failWithDataURL("no such url", new IllegalArgumentException(
				"Illegal data url 'no such url': no protocol: no such url"));
		// valid URL, but not URI, at least by Java's standards
		failWithDataURL("https://foo^bar.com/", new IllegalArgumentException(
				"Illegal data url 'https://foo^bar.com/': Illegal character in authority at " +
				"index 8: https://foo^bar.com/"));

		// class input
		failWithDataURL(new URL("https://foo^bar.com/"), new IllegalArgumentException(
				"Illegal data url 'https://foo^bar.com/': Illegal character in authority at " +
				"index 8: https://foo^bar.com/"));
	}

	@Test
	public void withResourceURLFail() throws Exception {
		// string input
		failWithResourceURL("no such url", new IllegalArgumentException(
				"Illegal resource url 'no such url': no protocol: no such url"));
		// valid URL, but not URI, at least by Java's standards
		failWithResourceURL("https://foo^bar.com/", new IllegalArgumentException(
				"Illegal resource url 'https://foo^bar.com/': Illegal character in authority at " +
				"index 8: https://foo^bar.com/"));

		// class input
		failWithResourceURL(new URL("https://foo^bar.com/"), new IllegalArgumentException(
				"Illegal resource url 'https://foo^bar.com/': Illegal character in authority at " +
				"index 8: https://foo^bar.com/"));
	}

	private void failWithDataURL(final String url, final Exception expected) {
		try {
			ExternalData.getBuilder().withDataURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	private void failWithResourceURL(final String url, final Exception expected) {
		try {
			ExternalData.getBuilder().withResourceURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	private void failWithDataURL(final URL url, final Exception expected) {
		try {
			ExternalData.getBuilder().withDataURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	private void failWithResourceURL(final URL url, final Exception expected) {
		try {
			ExternalData.getBuilder().withResourceURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

	@Test
	public void buildFail() throws Exception {
		try {
			ExternalData.getBuilder().build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"At least one field in an external data unit must be provided"));
		}
	}
}
