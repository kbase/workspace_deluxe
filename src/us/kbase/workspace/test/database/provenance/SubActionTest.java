package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.ES;

import java.net.URL;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.SubAction;

public class SubActionTest {
	
	private static final Optional<URL> EU = Optional.empty();
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(SubAction.class).usingGetClass().verify();
	}
	
	@Test
	public void buildMinimal() throws Exception {
		// since at least one field is required, build twice with different single fields
		final SubAction sa1 = SubAction.getBuilder().withCommit("commmmmmit").build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("commmmmmit")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		final SubAction sa2 = SubAction.getBuilder().withVersion("1").build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa2.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa2.getName(), is(ES));
		assertThat("incorrect version", sa2.getVersion(), is(opt("1")));
	}
	
	@Test
	public void buildMinimalOneFieldAtATime() throws Exception {
		final SubAction.Builder b = SubAction.getBuilder().withCodeURL("https://kbase.us");
		final SubAction sa1 = b.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(opt(new URL("https://kbase.us"))));
		assertThat("incorrect commit", sa1.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		b.withCodeURL("").withCommit("foobar");
		final SubAction sa2 = b.build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(opt("foobar")));
		
		b.withCommit(null).withEndpointURL("file:/home/user/turtlesinheat.mp4");
		final SubAction sa3 = b.build();
		assertThat("incorrect commit", sa3.getCommit(), is(ES));
		assertThat("incorrect endpoint URL",
				sa3.getEndpointURL(), is(opt(new URL("file:/home/user/turtlesinheat.mp4"))));
		
		b.withEndpointURL("").withName("name goes here");
		final SubAction sa4 = b.build();
		assertThat("incorrect endpoint URL", sa4.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa4.getName(), is(opt("name goes here")));
		
		b.withName(null).withVersion("v120.67.893");
		final SubAction sa5 = b.build();
		assertThat("incorrect name", sa5.getName(), is(ES));
		assertThat("incorrect version", sa5.getVersion(), is(opt("v120.67.893")));
	}
	
	@Test
	public void buildMinimalWithStringURLsWithNulls() throws Exception {
		// since at least one field is required, build twice with different single fields
		final SubAction sa1 = SubAction.getBuilder()
				.withCommit("c")
				.withCodeURL((String) null)
				.withEndpointURL((String) null)
				.withName(null)
				.withVersion(null)
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("c")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		final SubAction sa2 = SubAction.getBuilder()
				.withCommit(null)
				.withCodeURL((String) null)
				.withEndpointURL((String) null)
				.withName(null)
				.withVersion("v")
				.build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa2.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa2.getName(), is(ES));
		assertThat("incorrect version", sa2.getVersion(), is(opt("v")));
	}
	
	@Test
	public void buildMinimalWithStringURLsWithEmptyStrings() throws Exception {
		// since at least one field is required, build twice with different single fields
		final String es = "     \t      ";
		final SubAction sa1 = SubAction.getBuilder()
				.withCommit("c1")
				.withCodeURL(es)
				.withEndpointURL(es)
				.withName(es)
				.withVersion(es)
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("c1")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		final SubAction sa2 = SubAction.getBuilder()
				.withCommit(es)
				.withCodeURL(es)
				.withEndpointURL(es)
				.withName(es)
				.withVersion("v1")
				.build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa2.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa2.getName(), is(ES));
		assertThat("incorrect version", sa2.getVersion(), is(opt("v1")));
	}
	
	@Test
	public void buildMinimalWithClassURLsWithNulls() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCommit("c2")
				.withCodeURL((URL) null)
				.withEndpointURL((URL) null)
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("c2")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
	}
	
	@Test
	public void buildMaximalWithStringURLs() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCodeURL("  \t  https://github.com/kbase/AlanAldaInSpandex.git   ")
				.withCommit("   it's super serious   \t   ")
				.withEndpointURL("   http://insecure.com"    )
				.withName("   frank   \t")
				.withVersion(" large numbers!   \t ")
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(),
				is(opt(new URL("https://github.com/kbase/AlanAldaInSpandex.git"))));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("it's super serious")));
		assertThat("incorrect endpoint URL",
				sa1.getEndpointURL(), is(opt(new URL("http://insecure.com"))));
		assertThat("incorrect name", sa1.getName(), is(opt("frank")));
		assertThat("incorrect version", sa1.getVersion(), is(opt("large numbers!")));
	}
	
	@Test
	public void buildWithClassURLs() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCodeURL(new URL("http://secure.as.a.russian.walkie.talkie.com"))
				.withEndpointURL(new URL("https://foo.com"))
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(),
				is(opt(new URL("http://secure.as.a.russian.walkie.talkie.com"))));
		assertThat("incorrect commit", sa1.getCommit(), is(ES));
		assertThat("incorrect endpoint URL",
				sa1.getEndpointURL(), is(opt(new URL("https://foo.com"))));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
	}
	
	@Test
	public void buildAndOverwriteAllWithEmptyStrings() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCommit("c")
				.withCodeURL("https://github.com/kbase/AlanAldaInSpandex.git")
				.withCodeURL("    \t    ")
				.withEndpointURL("   http://insecure.com"    )
				.withEndpointURL("   ")
				.withName("   frank   \t")
				.withName("    \t    ")
				.withVersion(" large numbers!   \t ")
				.withVersion("     ")
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("c")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		final SubAction sa2 = SubAction.getBuilder()
				.withCommit("c")
				.withCommit("    \t   ")
				.withVersion("six")
				.build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa2.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa2.getName(), is(ES));
		assertThat("incorrect version", sa2.getVersion(), is(opt("six")));
	}
	
	@Test
	public void buildAndOverwriteAllWithNulls() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCommit("c")
				.withCodeURL("https://github.com/kbase/AlanAldaInSpandex.git")
				.withCodeURL((URL) null)
				.withEndpointURL("   http://insecure.com"    )
				.withEndpointURL((URL) null)
				.withName("   frank   \t")
				.withName(null)
				.withVersion(" large numbers!   \t ")
				.withVersion(null)
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa1.getCommit(), is(opt("c")));
		assertThat("incorrect endpoint URL", sa1.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
		final SubAction sa2 = SubAction.getBuilder()
				.withCommit("c")
				.withCommit(null)
				.withVersion("six")
				.build();
		assertThat("incorrect code URL", sa2.getCodeURL(), is(EU));
		assertThat("incorrect commit", sa2.getCommit(), is(ES));
		assertThat("incorrect endpoint URL", sa2.getEndpointURL(), is(EU));
		assertThat("incorrect name", sa2.getName(), is(ES));
		assertThat("incorrect version", sa2.getVersion(), is(opt("six")));
	}
	
	@Test
	public void buildAndOverwriteStringURLs() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCodeURL("https://github.com/kbase/AlanAldaInSpandex.git")
				.withCodeURL(new URL("https://thankgoodnessthatsover.com"))
				.withEndpointURL("   http://insecure.com"    )
				.withEndpointURL(new URL("mailto://yermum@meta.com"))
				.build();
		assertThat("incorrect code URL",
				sa1.getCodeURL(), is(opt(new URL("https://thankgoodnessthatsover.com"))));
		assertThat("incorrect commit", sa1.getCommit(), is(ES));
		assertThat("incorrect endpoint URL",
				sa1.getEndpointURL(), is(opt(new URL("mailto://yermum@meta.com"))));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
		
	}
	
	@Test
	public void buildAndOverwriteClassURLs() throws Exception {
		final SubAction sa1 = SubAction.getBuilder()
				.withCodeURL(new URL("https://thankgoodnessthatsover.com"))
				.withCodeURL("https://github.com/kbase/AlanAldaInSpandex.git")
				.withEndpointURL(new URL("mailto://yermum@meta.com"))
				.withEndpointURL("   http://insecure.com"    )
				.build();
		assertThat("incorrect code URL", sa1.getCodeURL(),
				is(opt(new URL("https://github.com/kbase/AlanAldaInSpandex.git"))));
		assertThat("incorrect commit", sa1.getCommit(), is(ES));
		assertThat("incorrect endpoint URL",
				sa1.getEndpointURL(), is(opt(new URL("http://insecure.com"))));
		assertThat("incorrect name", sa1.getName(), is(ES));
		assertThat("incorrect version", sa1.getVersion(), is(ES));
	}
	
	@Test
	public void withURLFail() throws Exception {
		// string input
		failWithURL("tincupstring://foo.com", new IllegalArgumentException(
				"Illegal url 'tincupstring://foo.com': unknown protocol: tincupstring"));
		// valid URL, but not URI, at least by Java's standards
		failWithURL("https://kb^ase.us/", new IllegalArgumentException(
				"Illegal url 'https://kb^ase.us/': Illegal character in authority at " +
				"index 8: https://kb^ase.us/"));
		
		// class input
		failWithURL(new URL("https://kb^ase.us/"), new IllegalArgumentException(
				"Illegal url 'https://kb^ase.us/': Illegal character in authority at " +
				"index 8: https://kb^ase.us/"));
	}
	
	private void failWithURL(final String url, final Exception expected) {
		try {
			SubAction.getBuilder().withCodeURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		try {
			SubAction.getBuilder().withEndpointURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	private void failWithURL(final URL url, final Exception expected) {
		try {
			SubAction.getBuilder().withCodeURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		try {
			SubAction.getBuilder().withEndpointURL(url);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void buildFail() throws Exception {
		try {
			SubAction.getBuilder().build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"At least one field in a provenance sub action must be provided"));
		}
	}

}
