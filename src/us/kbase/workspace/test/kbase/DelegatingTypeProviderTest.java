package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static us.kbase.common.test.TestCommon.list;
import static us.kbase.common.test.TestCommon.LONG1001;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;

import org.junit.Test;

import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.test.TestCommon;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeProvider.ResolvedType;
import us.kbase.typedobj.core.TypeProvider.TypeFetchException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.kbase.DelegatingTypeProvider;

public class DelegatingTypeProviderTest {

	@Test
	public void constants() throws Exception {
		assertThat("incorrect type cache time",
				DelegatingTypeProvider.DEFAULT_TYPE_CACHE_TIME_MS, is(300000));
		assertThat("incorrect type cache size",
				DelegatingTypeProvider.DEFAULT_TYPE_CACHE_MAX_SIZE, is(131072));
		assertThat("incorrect jsonschema cache size",
				DelegatingTypeProvider.DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE, is(1048576));
	}
	
	private class FakeTicker implements Ticker {

		private final AtomicLong nanos = new AtomicLong();

		public FakeTicker advance(long nanoseconds) {
			nanos.addAndGet(nanoseconds);
			return this;
		}

		@Override
		public long read() {
			return nanos.get();
		}
	}
	
	// #### Cacheless tests ####
	
	private static class TestMocks {
		private final WorkspaceClient ws;
		private final DelegatingTypeProvider p;
		private final FakeTicker t;

		public TestMocks(
				final WorkspaceClient ws,
				final DelegatingTypeProvider p,
				final FakeTicker t) {
			this.ws = ws;
			this.p = p;
			this.t = t;
		}
	}
	
	private Exception failGetJsonSchema(
			final DelegatingTypeProvider p,
			final TypeDefId type,
			final Exception expected) {
		try {
			p.getTypeJsonSchema(type);
			fail("expected exception");
			return null;
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
			return got;
		}
	}
	
	private TestMocks initMocks() {
		return initMocks(0, 0, 1024 * 1024);
	}
	
	private TestMocks initMocks(
			final int typeCacheTimeMS,
			final int typeCacheSize,
			final int jsonSchemaCacheSize) {
		return initMocks(typeCacheTimeMS, typeCacheSize, jsonSchemaCacheSize, false);
	}
	
	private TestMocks initMocks(
			final int typeCacheTimeMS,
			final int typeCacheSize,
			final int jsonSchemaCacheSize,
			final boolean evictSynchronously) {
		return _initMocks(typeCacheTimeMS, typeCacheSize, jsonSchemaCacheSize, new FakeTicker(),
				evictSynchronously);
	}
	
	private TestMocks _initMocks(
			final int typeCacheTimeMS,
			final int typeCacheSize,
			final int jsonSchemaCacheSize,
			final FakeTicker ticker,
			final boolean evictSynchronously) {
		final WorkspaceClient cli = mock(WorkspaceClient.class);
		
		final DelegatingTypeProvider dtp = DelegatingTypeProvider.getBuilder(cli)
				.withJsonSchemaCacheMaxSize(jsonSchemaCacheSize)
				.withTypeCacheMaxSize(typeCacheSize)
				.withTypeCacheTimeMS(typeCacheTimeMS)
				.withCacheTicker(ticker)
				.withRunCacheEvictionsOnCurrentThread(evictSynchronously)
				.build();
		return new TestMocks(cli, dtp, ticker);
	}
	
	private TypeDefId type(final String type) {
		return TypeDefId.fromTypeString(type);
	}
	
	private AbsoluteTypeDefId atype(final String type) {
		return AbsoluteTypeDefId.fromAbsoluteTypeString(type);
	}
	
	@Test
	public void getJsonSchemaForNonAbsoluteType() throws Exception {
		final TestMocks m = initMocks();
		
		when(m.ws.getTypeInfo("Foo.Bar-6")).thenReturn(
				new TypeInfo()
					.withJsonSchema("jsonschema here")
					.withTypeDef("Foo.Bar-6.3"),
				(TypeInfo) null); // ensure only called once
		assertThat(m.p.getTypeJsonSchema(type("Foo.Bar-6")),
				is(new ResolvedType(atype("Foo.Bar-6.3"), "jsonschema here")));
	}
	
	@Test
	public void getJsonSchemaForAbsoluteType() throws Exception {
		final TestMocks m = initMocks();
		
		when(m.ws.getTypeInfo("Foo.Bar-6.2")).thenReturn(
				new TypeInfo()
					.withJsonSchema("jsonschema here")
					.withTypeDef("Foo.Bar-6.2"),
				(TypeInfo) null); // ensure only called once
		
		assertThat(m.p.getTypeJsonSchema(type("Foo.Bar-6.2")),
				is(new ResolvedType(atype("Foo.Bar-6.2"), "jsonschema here")));
	}
	
	// #### Build error tests ####
	
	@Test
	public void getBuilderFail() throws Exception {
		try {
			DelegatingTypeProvider.getBuilder(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("client"));
		}
	}
	
	@Test
	public void withTypeCacheTimeMSFail() throws Exception {
		final WorkspaceClient c = mock(WorkspaceClient.class);
		for (final int time: list(-1, -100, -1000000000)) {
			try {
				DelegatingTypeProvider.getBuilder(c).withTypeCacheTimeMS(time);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"typeCacheTimeMS must be >= 0"));
			}
		}
	}
	
	@Test
	public void withTypeCacheMaxSizeFail() throws Exception {
		final WorkspaceClient c = mock(WorkspaceClient.class);
		for (final int size: list(-1, -100, -1000000000)) {
			try {
				DelegatingTypeProvider.getBuilder(c).withTypeCacheMaxSize(size);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"typeCacheMaxSize must be >= 0"));
			}
		}
	}
	
	@Test
	public void withJsonSchemaCacheMaxSizeFail() throws Exception {
		final WorkspaceClient c = mock(WorkspaceClient.class);
		for (final int size: list(1024 * 1024 - 1, 1000000, 100, 0, -1, -100, -1000000000)) {
			try {
				DelegatingTypeProvider.getBuilder(c).withJsonSchemaCacheMaxSize(size);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"jsonschemaCacheMaxSize must be >= 1048576"));
			}
		}
	}
	
	// #### Type fetch error tests ####
	
	@Test
	public void getJsonSchemaFailNoSuchModule() throws Exception {
		// test with absolute & non-absolute types
		for (final String type: list("Foo.Baz-32", "Foo.Baz-32.19")) {
			final List<String> errors = list(
					"Module doesn't exist: Foo",
					"Module Foo was not initialized. For that you must request ownership of the "
							+ "module, and your request must be approved.",
					"Module wasn't uploaded: Foo"
					);
			for (final String err: errors) {
				final TestMocks m = initMocks();
				when(m.ws.getTypeInfo(type)).thenThrow(
						new ServerException(err, 1, "somename", "stack\ntrace\nlline 1: i++"));
				final Exception got = failGetJsonSchema(
						m.p, TypeDefId.fromTypeString(type),
						new NoSuchModuleException(err));
				TestCommon.assertExceptionCorrect(
						got.getCause(), new ServerException(err, 1, "somename"));
			}
		}
	}
	
	@Test
	public void getJsonSchemaForNonAbsoluteTypeFailNoSuchType() throws Exception {
		// test with absolute & non-absolute types
		for (final String type: list("Foo.Baz-32", "Foo.Baz-32.19")) {
			final List<String> errors = list(
					"This type wasn't released yet and you should be an owner to access "
							+ "unreleased version information",
					"Unable to locate type: Foo.Baz"
					);
			for (final String err: errors) {
				final TestMocks m = initMocks();
				when(m.ws.getTypeInfo(type)).thenThrow(
						new ServerException(err, 1, "somename", "stack\ntrace\nlline 1: i++"));
				final Exception got = failGetJsonSchema(m.p, TypeDefId.fromTypeString(type),
						new NoSuchTypeException(err));
				TestCommon.assertExceptionCorrect(
						got.getCause(), new ServerException(err, 1, "somename"));
			}
		}
	}
	
	@Test
	public void getJsonSchemaForNonAbsoluteTypeFailServerException() throws Exception {
		// test with absolute & non-absolute types
		for (final String type: list("Foo.Baz-32", "Foo.Baz-32.19")) {
			final TestMocks m = initMocks();
			when(m.ws.getTypeInfo(type)).thenThrow(
					new ServerException("aw crap", 1, "somename", "stack\ntrace\nlline 1: i++"));
			final Exception got = failGetJsonSchema(m.p, TypeDefId.fromTypeString(type),
					new TypeFetchException("Failed retrieving type info from remote type "
							+ "database: aw crap"));
			TestCommon.assertExceptionCorrect(
					got.getCause(), new TypeFetchException("stack\ntrace\nlline 1: i++"));
			TestCommon.assertExceptionCorrect(
						got.getCause().getCause(), new ServerException("aw crap", 1, "somename"));
		}
	}
	
	@Test
	public void getJsonSchemaForNonAbsoluteTypeFailIOException() throws Exception {
		// test with absolute & non-absolute types
		for (final String type: list("Foo.Baz-32", "Foo.Baz-32.19")) {
			final TestMocks m = initMocks();
			when(m.ws.getTypeInfo(type)).thenThrow(new IOException("poop"));
			final Exception got = failGetJsonSchema(m.p, TypeDefId.fromTypeString(type),
					new TypeFetchException("Failed retrieving type info from remote type "
							+ "database: poop"));
			TestCommon.assertExceptionCorrect(got.getCause(), new IOException("poop"));
		}
	}
	
	@Test
	public void getJsonSchemaForNonAbsoluteTypeFailJsonClientException() throws Exception {
		// test with absolute & non-absolute types
		for (final String type: list("Foo.Baz-32", "Foo.Baz-32.19")) {
			final TestMocks m = initMocks();
			when(m.ws.getTypeInfo(type)).thenThrow(new JsonClientException("dang bruh"));
			final Exception got = failGetJsonSchema(m.p, TypeDefId.fromTypeString(type),
					new TypeFetchException("Failed retrieving type info from remote type "
							+ "database: dang bruh"));
			TestCommon.assertExceptionCorrect(
					got.getCause(), new JsonClientException("dang bruh"));
		}
	}
	
	// #### Cache invalidation tests ####
	
	@Test
	public void typeCacheEvictOnTime() throws Exception {
		final TestMocks m = initMocks(5, 1000000, 1024*1024);
		
		when(m.ws.getTypeInfo("Foo.Bar-6")).thenReturn(
				new TypeInfo()
					.withTypeDef("Foo.Bar-6.3")
					.withJsonSchema("jsonschema here"),
				new TypeInfo()
					.withTypeDef("Foo.Bar-6.4")
					.withJsonSchema("jsonschema v2 here")
					);
		when(m.ws.getTypeInfo("Foo.Baz-2")).thenReturn(
				new TypeInfo()
					.withTypeDef("Foo.Baz-2.1")
					.withJsonSchema("other jsonschema here"),
				(TypeInfo) null // ensure only called once
				);
		
		assertThat(m.p.getTypeJsonSchema(type("Foo.Bar-6")),
				is(new ResolvedType(atype("Foo.Bar-6.3"), "jsonschema here")));
		m.t.advance(1); // anything called after this won't expire in this test
		assertThat(m.p.getTypeJsonSchema(type("Foo.Baz-2")),
				is(new ResolvedType(atype("Foo.Baz-2.1"), "other jsonschema here")));
		m.t.advance(4999998);
		assertThat(m.p.getTypeJsonSchema(type("Foo.Bar-6")),
				is(new ResolvedType(atype("Foo.Bar-6.3"), "jsonschema here")));
		assertThat(m.p.getTypeJsonSchema(type("Foo.Baz-2")),
				is(new ResolvedType(atype("Foo.Baz-2.1"), "other jsonschema here")));
		m.t.advance(1);
		assertThat(m.p.getTypeJsonSchema(type("Foo.Bar-6")),
				is(new ResolvedType(atype("Foo.Bar-6.4"), "jsonschema v2 here")));
		assertThat(m.p.getTypeJsonSchema(type("Foo.Baz-2")),
				is(new ResolvedType(atype("Foo.Baz-2.1"), "other jsonschema here")));
		
		verify(m.ws, times(2)).getTypeInfo("Foo.Bar-6");
	}
	
	@Test
	public void typeCacheEvictOnWeight() throws Exception {
		// The cache uses the Window TinyFLU eviction algorithm which makes it pretty
		// hard to test, see https://9vx.org/post/on-window-tinylfu/

		// Type names cannot have non-ASCII chars, so no 4 byte UTF-16 characters are possible
		final TestMocks m = initMocks(1000000, 80, 1024*1024, true);
		// Note that the weight is the length of the keys in chars * 2 since in memory strings
		// are stored in UTF-16
		
		when(m.ws.getTypeInfo("Fo.Bar-6")).thenReturn(
				new TypeInfo()
					.withTypeDef("Fo.Bar-6.3")
					.withJsonSchema("jsonschema 1 here"),
				new TypeInfo()
					.withTypeDef("Fo.Bar-6.4")
					.withJsonSchema("jsonschema 1.1 here")
					);
		when(m.ws.getTypeInfo("Fo.Bas-2")).thenReturn(
				new TypeInfo()
					.withTypeDef("Fo.Bas-2.1")
					.withJsonSchema("jsonschema 2 here"),
				(TypeInfo) null); // only called once
		final TypeInfo ti = new TypeInfo()
				.withTypeDef("Fo.Bat-1.7")
				.withJsonSchema("jsonschema 3 here");
		final TypeInfo ti2 = new TypeInfo()
				.withTypeDef("Fo.Bat-1.9")
				.withJsonSchema("jsonschema 3.1 here");
		when(m.ws.getTypeInfo("Fo.Bat-1")).thenReturn(ti, ti, ti2);
		
		// the cache eviction policy is not strict LRU, it also takes frequency into account,
		// so we have to do a bit of wrangling to get the evictions we want
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bar-6")),
				is(new ResolvedType(atype("Fo.Bar-6.3"), "jsonschema 1 here")));
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bas-2")),
				is(new ResolvedType(atype("Fo.Bas-2.1"), "jsonschema 2 here")));
		// cache should now be full
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bar-6")),
				is(new ResolvedType(atype("Fo.Bar-6.3"), "jsonschema 1 here")));
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bas-2")),
				is(new ResolvedType(atype("Fo.Bas-2.1"), "jsonschema 2 here")));
		
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bat-1")),
				is(new ResolvedType(atype("Fo.Bat-1.7"), "jsonschema 3 here")));
		// call multiple times to handle immediate frequency based eviction and evict 1st entry
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bat-1")),
				is(new ResolvedType(atype("Fo.Bat-1.7"), "jsonschema 3 here")));
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bar-6")),
				is(new ResolvedType(atype("Fo.Bar-6.4"), "jsonschema 1.1 here")));
		// call multiple times to evict 3rd entry
		// that 2nd entry is really stuck in there
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bar-6")),
				is(new ResolvedType(atype("Fo.Bar-6.4"), "jsonschema 1.1 here")));
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bar-6")),
				is(new ResolvedType(atype("Fo.Bar-6.4"), "jsonschema 1.1 here")));
		
		assertThat(m.p.getTypeJsonSchema(type("Fo.Bat-1")),
				is(new ResolvedType(atype("Fo.Bat-1.9"), "jsonschema 3.1 here")));
		
		// I'm not really sure I understand what's going on here but it does show the cache
		// is being updated and entries are getting evicted which is the main
		// thing
	}
	
	@Test
	public void jsonSchemaCacheEvictOnWeight() throws Exception {
		// See the notes for the type cash test above. Testing cache eviction here is
		// difficult to do without a really clear understanding of the cache eviction algorithm
		// which is pretty complex and is not worth the time to get my head around right now
		
		// the relative -> absolute type cache never evicts for this test
		final TestMocks m = initMocks(1000000, 10000000, 1200000, true);
		
		final String s1000 = LONG1001.substring(0, LONG1001.length() - 1);
		final String s100000 = lngstr(s1000, 100);
		final String s399980 = lngstr(s100000, 4).substring(0, 399980);
		final String s399980mod = s399980.substring(0, 399979) + "1";
		final String s599980 = lngstr(s100000, 6).substring(0, 599980);
		final String s599980mod = s599980.substring(0, 599979) + "1";
		final String s199981 = lngstr(s100000, 2).substring(0, 199981);
		
		when(m.ws.getTypeInfo("Wo.We-7")).thenReturn(
				new TypeInfo()
					.withTypeDef("Wo.We-7.1")
					.withJsonSchema(s399980)
					);
		when(m.ws.getTypeInfo("Wo.We-7.1")).thenReturn(
				new TypeInfo()
					.withTypeDef("Wo.We-7.1")
					// this should never happen in practice but we change the jsonschmea for
					// testing purposes
					.withJsonSchema(s399980mod)
					);
		when(m.ws.getTypeInfo("Wo.Wx-2")).thenReturn(
				new TypeInfo()
					.withTypeDef("Wo.Wx-2.7")
					.withJsonSchema(s599980)
					);
		when(m.ws.getTypeInfo("Wo.Wx-2.7")).thenReturn(
				new TypeInfo()
					.withTypeDef("Wo.Wx-2.7")
					.withJsonSchema(s599980mod)
					);
		when(m.ws.getTypeInfo("Wo.Wz-9")).thenReturn(
				new TypeInfo()
					.withTypeDef("Wo.Wz-9.3")
					.withJsonSchema(s199981)
					);
		
		assertThat(m.p.getTypeJsonSchema(type("Wo.We-7")),
				is(new ResolvedType(atype("Wo.We-7.1"), s399980)));
		assertThat(m.p.getTypeJsonSchema(type("Wo.Wx-2")),
				is(new ResolvedType(atype("Wo.Wx-2.7"), s599980)));
		// adding any more items will cause an eviction
		assertThat(m.p.getTypeJsonSchema(type("Wo.We-7")),
				is(new ResolvedType(atype("Wo.We-7.1"), s399980)));
		assertThat(m.p.getTypeJsonSchema(type("Wo.Wx-2")),
				is(new ResolvedType(atype("Wo.Wx-2.7"), s599980)));
		
		assertThat(m.p.getTypeJsonSchema(type("Wo.Wz-9")),
				is(new ResolvedType(atype("Wo.Wz-9.3"), s199981)));
		// first entry is evicted at this point
		assertThat(m.p.getTypeJsonSchema(type("Wo.We-7")),
				is(new ResolvedType(atype("Wo.We-7.1"), s399980mod)));
		// 2nd entry is evicted at this point
		assertThat(m.p.getTypeJsonSchema(type("Wo.Wx-2")),
				is(new ResolvedType(atype("Wo.Wx-2.7"), s599980mod)));
	}
	
	private String lngstr(final String s, final int reps) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < reps; i++) {
			sb.append(s);
		}
		return sb.toString();
	}
	
}
