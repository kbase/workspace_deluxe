package us.kbase.test.workspace.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static us.kbase.test.common.TestCommon.list;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import us.kbase.test.common.TestCommon;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFileListener;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.ByteArrayFileCacheManager;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;

public class ByteArrayFileCacheManagerTest {
	
	private static final TempFilesManager TFM = new TempFilesManager(
			Paths.get(TestCommon.getTempDir()).toFile());
	
	@Before
	public void before() {
		TFM.cleanup();
	}
	
	@Test
	public void isStoredOnDisk() throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		assertThat("incorrect on disk", m.isStoringOnDisk(), is(false));
		
		final ByteArrayFileCacheManager m2 = new ByteArrayFileCacheManager(null);
		assertThat("incorrect on disk", m2.isStoringOnDisk(), is(false));
		
		final TempFilesManager tfm = mock(TempFilesManager.class);
		final ByteArrayFileCacheManager m3 = new ByteArrayFileCacheManager(tfm);
		assertThat("incorrect on disk", m3.isStoringOnDisk(), is(true));
	}
	
	@Test
	public void createAndDestroyBAFCInMem() throws Exception {
		createAndDestroyBAFCInMem(true, true);
		createAndDestroyBAFCInMem(false, true);
		createAndDestroyBAFCInMem(true, false);
		createAndDestroyBAFCInMem(false, false);
	}

	public void createAndDestroyBAFCInMem(final boolean trustedJson, final boolean sorted)
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final InputStream is = new ByteArrayInputStream("{\"foo\": \"bar\"}".getBytes());
		
		final ByteArrayFileCache b = m.createBAFC(is, trustedJson, sorted);
		
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(0));
		assertThat("incorrect sorted", b.isSorted(), is(sorted));
		assertThat("incorrect trusted json", b.containsTrustedJson(), is(trustedJson));
		assertThat("incorrect size", b.getSize(), is(14L));
		assertThat("incorrect JSON", IOUtils.toString(b.getJSON()), is("{\"foo\": \"bar\"}"));
		assertThat("incorrect UObject", b.getUObject().asClassInstance(Map.class),
				is(ImmutableMap.of("foo", "bar")));
		assertThat("incorrect destroyed", b.isDestroyed(), is(false));
		
		destroyTwice(b);
	}
	
	@Test
	public void createAndDestroyBAFCOnDisk() throws Exception {
		createAndDestroyBAFCOnDisk(true, true);
		createAndDestroyBAFCOnDisk(false, true);
		createAndDestroyBAFCOnDisk(true, false);
		createAndDestroyBAFCOnDisk(false, false);
	}
		
	public void createAndDestroyBAFCOnDisk(final boolean trustedJson, final boolean sorted)
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager(TFM);
		final InputStream is = new ByteArrayInputStream("{\"foo\": \"bar\"}".getBytes());
		
		final ByteArrayFileCache b = m.createBAFC(is, trustedJson, sorted);
		
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(1));
		assertThat("incorrect sorted", b.isSorted(), is(sorted));
		assertThat("incorrect trusted json", b.containsTrustedJson(), is(trustedJson));
		assertThat("incorrect size", b.getSize(), is(14L));
		assertThat("incorrect JSON", IOUtils.toString(b.getJSON()), is("{\"foo\": \"bar\"}"));
		assertThat("incorrect UObject", b.getUObject().asClassInstance(Map.class),
				is(ImmutableMap.of("foo", "bar")));
		assertThat("incorrect destroyed", b.isDestroyed(), is(false));
		
		destroyTwice(b);
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(0));
	}
	
	private void destroyTwice(final ByteArrayFileCache b) {
		b.destroy();
		checkDestroyed(b);
		b.destroy(); // should be a noop
		checkDestroyed(b);
	}

	private void checkDestroyed(final ByteArrayFileCache b) {
		assertThat("incorrect destroyed", b.isDestroyed(), is(true));
		
		failDestroyed(b, x -> x.containsTrustedJson());
		failDestroyed(b, x -> x.getJSON());
		failDestroyed(b, x -> x.getUObject());
	}
	
	@FunctionalInterface
	private static interface ConsumerWithException<T> {
		
		public void accept(T t) throws Exception;
	}
	
	private void failDestroyed(
			final ByteArrayFileCache b,
			final ConsumerWithException<ByteArrayFileCache> func) {
		try {
			func.accept(b);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(
					got, new RuntimeException("This ByteArrayFileCache is destroyed"));
		}
	}
	
	@Test
	public void createBAFCFailNull() throws Exception {
		failCreateBAFC(null, (InputStream) null, new NullPointerException("input"));
	}
	
	@Test
	public void createBAFCFailIOExceptionWithoutFile() throws Exception {
		failCreateBAFC(null, new IOException("rats"), new IOException("rats"));
	}
	
	@Test
	public void createBAFCFailRuntimeExceptionWithoutFile() throws Exception {
		failCreateBAFC(null, new RuntimeException("re"), new RuntimeException("re"));
	}

	@Test
	public void createBAFCFailIOExceptionWithFile() throws Exception {
		failCreateBAFCWithFile(new IOException("rats"), new IOException("rats"));
	}
	
	@Test
	public void createBAFCFailRuntimeExceptionWithFile() throws Exception {
		failCreateBAFCWithFile(new RuntimeException("rats"), new RuntimeException("rats"));
	}

	public void failCreateBAFCWithFile(final Exception toThrow, final Exception expected)
			throws Exception {
		final List<File> createdFiles = new LinkedList<>(); 
		final TempFileListener listener = f -> createdFiles.add(f);
		TFM.addListener(listener);
		try {
			failCreateBAFC(TFM, toThrow, expected);
			assertThat("incorrect files", createdFiles.size(), is(1));
			assertThat("didn't delete file", TFM.getTempFileList().size(), is(0));
		} finally {
			TFM.removeListener(listener);
		}
	}

	private void failCreateBAFC(
			final TempFilesManager tfm,
			final Exception toThrow,
			final Exception expected)
					throws Exception {
		final InputStream is = mock(InputStream.class);
		doThrow(toThrow).when(is).read(any());
		failCreateBAFC(tfm, is, expected);
	}
	
	public void failCreateBAFC(
			final TempFilesManager tfm,
			final InputStream is,
			final Exception expected) {
		try {
			new ByteArrayFileCacheManager(tfm).createBAFC(is, false, false);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void getSubdataExtractionInMemory() throws Exception {
		getSubdataExtractionInMemory(true, true);
		getSubdataExtractionInMemory(false, true);
		getSubdataExtractionInMemory(true, false);
		getSubdataExtractionInMemory(false, false);
	}
	
	private void getSubdataExtractionInMemory(final boolean trustedJson, final boolean sorted)
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final InputStream is = new ByteArrayInputStream(
				"{\"baz\": \"bat\", \"foo\": \"bar\"}".getBytes());
		final ByteArrayFileCache parent = m.createBAFC(is, trustedJson, sorted);
		final ByteArrayFileCache subset = m.getSubdataExtraction(
				parent, new SubsetSelection(list("/foo")));
		
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(0));
		assertThat("incorrect sorted", subset.isSorted(), is(sorted));
		assertThat("incorrect trusted json", subset.containsTrustedJson(), is(trustedJson));
		assertThat("incorrect size", subset.getSize(), is(13L));
		assertThat("incorrect JSON", IOUtils.toString(subset.getJSON()), is("{\"foo\":\"bar\"}"));
		assertThat("incorrect UObject", subset.getUObject().asClassInstance(Map.class),
				is(ImmutableMap.of("foo", "bar")));
		assertThat("incorrect destroyed", subset.isDestroyed(), is(false));
		
		destroyTwice(subset);
		checkDestroyed(parent);
	}
	
	@Test
	public void getSubdataExtractionOnDisk() throws Exception {
		getSubdataExtractionOnDisk(true, true);
		getSubdataExtractionOnDisk(false, true);
		getSubdataExtractionOnDisk(true, false);
		getSubdataExtractionOnDisk(false, false);
	}
	
	private void getSubdataExtractionOnDisk(final boolean trustedJson, final boolean sorted)
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager(TFM);
		final InputStream is = new ByteArrayInputStream(
				"{\"baz\": \"bat\", \"foo\": \"bar\"}".getBytes());
		final ByteArrayFileCache parent = m.createBAFC(is, trustedJson, sorted);
		final ByteArrayFileCache subset = m.getSubdataExtraction(
				parent, new SubsetSelection(list("/foo")));
		
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(2));
		assertThat("incorrect sorted", subset.isSorted(), is(sorted));
		assertThat("incorrect trusted json", subset.containsTrustedJson(), is(trustedJson));
		assertThat("incorrect size", subset.getSize(), is(13L));
		assertThat("incorrect JSON", IOUtils.toString(subset.getJSON()), is("{\"foo\":\"bar\"}"));
		assertThat("incorrect UObject", subset.getUObject().asClassInstance(Map.class),
				is(ImmutableMap.of("foo", "bar")));
		assertThat("incorrect destroyed", subset.isDestroyed(), is(false));
		
		destroyTwice(subset);
		assertThat("incorrect file count", TFM.getTempFileList().size(), is(0));
		checkDestroyed(parent);
	}
	
	@Test
	public void getSubdataExtractionBadArgs() throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final ByteArrayFileCache b = m.createBAFC(
				new ByteArrayInputStream("{}".getBytes()), true, true);
		final SubsetSelection s = new SubsetSelection(list("/foo"));
		failGetSubdataExtraction(m, null, s, new NullPointerException("parent"));
		failGetSubdataExtraction(m, b, null, new NullPointerException("paths"));
		failGetSubdataExtraction(m, b, SubsetSelection.EMPTY, new IllegalArgumentException(
				"paths cannot be empty"));
		
		b.destroy();
		failGetSubdataExtraction(m, b, s, new RuntimeException(
				"This ByteArrayFileCache is destroyed"));
	}
	
	@Test
	public void getSubDataExtractionFailRuntimeExceptionWithoutFile() throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final ByteArrayFileCache b = m.createBAFC(
				new ByteArrayInputStream("{}".getBytes()), true, true);
		final SubsetSelection s = mock(SubsetSelection.class);
		
		when(s.size()).thenThrow(new RuntimeException("this is pretty filthy"));
		failGetSubdataExtraction(m, b, s, new RuntimeException("this is pretty filthy"));
	}
	
	@Test
	public void getSubDataExtractionFailTypeObjectExtractionExceptionWithoutFile()
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final ByteArrayFileCache b = m.createBAFC(
				new ByteArrayInputStream("{\"foo\": [1, 2]}".getBytes()), true, true);
		final SubsetSelection s = new SubsetSelection(list("/foo/2"));
		
		failGetSubdataExtraction(m, b, s, new TypedObjectExtractionException(
				"Invalid selection: no array element exists at position '2', at: /foo/2"));
	}
	
	@Test
	public void getSubDataExtractionFailRuntimeExceptionWithFile() throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager(TFM);
		
		final ByteArrayFileCache b = m.createBAFC(
				new ByteArrayInputStream("{}".getBytes()), true, true);
		final SubsetSelection s = mock(SubsetSelection.class);
		
		final RuntimeException expected = new RuntimeException("this is pretty filthy");
		when(s.size()).thenThrow(expected);
		
		failGetSubdataExtractionWithFile(m, b, s, expected);
	}
	
	@Test
	public void getSubDataExtractionFailTypeObjectExtractionExceptionWithFile()
			throws Exception {
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager(TFM);
		final ByteArrayFileCache b = m.createBAFC(
				new ByteArrayInputStream("{\"foo\": [1, 2]}".getBytes()), true, true);
		final SubsetSelection s = new SubsetSelection(list("/foo/2"));
		
		failGetSubdataExtractionWithFile(m, b, s, new TypedObjectExtractionException(
				"Invalid selection: no array element exists at position '2', at: /foo/2"));
	}

	public void failGetSubdataExtractionWithFile(
			final ByteArrayFileCacheManager dataMan,
			final ByteArrayFileCache parent,
			final SubsetSelection subset,
			final Exception expected) {
		final List<File> createdFiles = new LinkedList<>(); 
		final TempFileListener listener = f -> createdFiles.add(f);
		TFM.addListener(listener);
		try {
			failGetSubdataExtraction(dataMan, parent, subset, expected);
			assertThat("incorrect files", createdFiles.size(), is(1));
			// the parent is still around, so there should be one file left
			assertThat("didn't delete file", TFM.getTempFileList().size(), is(1));
		} finally {
			TFM.removeListener(listener);
		}
	}
	
	private void failGetSubdataExtraction(
			final ByteArrayFileCacheManager dataMan,
			final ByteArrayFileCache parent,
			final SubsetSelection subset,
			final Exception expected) {
		try {
			dataMan.getSubdataExtraction(parent, subset);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
		
	}
	
	@Test
	public void documentBugBehavior() throws Exception {
		/* This is less a test than documenting a current bug in BAFCM.
		 * It's kind of expensive to fix and we don't run into it in practice so leaving it
		 * alone for now.
		 * The bug is that since all created UObjects share the same
		 * JsonTokenStream, and also since subsetting the data resets the token stream,
		 * some operations with getUObject() and getSubdataExtraction() can fail if
		 * they're started before a prior operation has completely finished processing
		 * the data. The way to avoid this bug is to avoid accessing the JsonTokenStream
		 * directly and not create multiple UObjects and / or subsets before completing
		 * all current operations.
		 */
		
		final ByteArrayFileCacheManager m = new ByteArrayFileCacheManager();
		final ByteArrayInputStream is = new ByteArrayInputStream(
				"{\"baz\": \"bat\", \"foo\": \"bar\"}".getBytes());
		final ByteArrayFileCache b = m.createBAFC(is, true, true);
		
		// start processing the JsonTokenSteam but don't finish
		b.getUObject().getPlacedStream().nextToken();
		
		try {
			b.getUObject();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IOException(
					"Inner parser wasn't closed previously"));
		}
		try {
			m.getSubdataExtraction(b, new SubsetSelection(list("foo")));
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new TypedObjectExtractionException(
					"Inner parser wasn't closed previously"));
		}
	}

}
