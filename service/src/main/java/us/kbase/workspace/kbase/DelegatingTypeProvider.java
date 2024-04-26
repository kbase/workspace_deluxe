package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.UTF_16;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeProvider;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.workspace.TypeInfo;
import us.kbase.workspace.WorkspaceClient;

/** A type provider that delegates to another workspace service.
 * 
 * There are two internal caches - one that maps absolute type IDs to their jsonschema document,
 * and one that maps non-absolute type IDs to their respective absolute IDs. The size of
 * each cache can be configured as well as the residence time of the latter cache.
 */
public class DelegatingTypeProvider implements TypeProvider {
	
	/** The default amount of time a non-absolute to absolute type mapping should remain in the
	 * cache in milliseconds.
	 */
	public static final int DEFAULT_TYPE_CACHE_TIME_MS = 5 * 60 * 1000; // 5m between checks

	/** The default maximum size of the non-absolute to absolute type mapping cache in bytes.
	 * Mapping sizes are calculated as sum of the Java UTF-16 encoding size of the non-absolute
	 * type string and absolute type string. References, etc. are not accounted for.
	 */
	public static final int DEFAULT_TYPE_CACHE_MAX_SIZE = 128 * 1024;
	
	
	/** The default maximum size of the absolute type to JSONschema mapping cache in bytes.
	 * Mapping sizes are calculated as sum of the Java UTF-16 encoding size of the absolute
	 * type string and the size of the UTF-8 encoded JSON schema. References, etc. are not
	 * accounted for.
	 * 
	 * This value is the minimum size allowed for the maximum size of the cache as the caching
	 * logic depends on the jsonschema cache being populated.
	 */
	public static final int DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE = 1024 * 1024;
	
	private final WorkspaceClient client;
	private final Cache<String, String> typeCache;
	private final Cache<String, byte[]> jsonSchemaCache;

	private DelegatingTypeProvider(
			final WorkspaceClient client,
			final int typeCacheTimeMS,
			final int typeCacheMaxSize,
			final int jsonschemaCacheMaxSize,
			final Ticker cacheTicker,
			final boolean evictionsOnCurrentThread) {
		this.client = requireNonNull(client);
		// TODO JAVA11 upgrade Caffeine to v3.x after dumping java 8
		final Caffeine<String, String> tb = Caffeine.newBuilder()
				.expireAfterWrite(typeCacheTimeMS, TimeUnit.MILLISECONDS)
				.maximumWeight(typeCacheMaxSize)
				.weigher((String k, String v) ->  utf16Bytes(k) + utf16Bytes(v))
				.ticker(cacheTicker);
		// converting to byte saves 2x memory costs over char[]/string for ascii if we use utf8
		// encoding, since java uses utf16.
		final Caffeine<String, byte[]> jb = Caffeine.newBuilder()
				.maximumWeight(jsonschemaCacheMaxSize)
				.weigher((String k, byte[] v) -> utf16Bytes(k) + v.length);
		if (evictionsOnCurrentThread) {
			tb.executor(runnable -> runnable.run());
			jb.executor(runnable -> runnable.run());
		}
		typeCache = tb.build();
		jsonSchemaCache = jb.build();
	}
	
	private int utf16Bytes(final String s) {
		// memory inefficient but only expected to be used on tiny strings. Use a counting stream
		// if needed
		return s.getBytes(UTF_16).length;
	}

	@Override
	public ResolvedType getTypeJsonSchema(final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, TypeFetchException {
		if (requireNonNull(typeDefId, "typeDefId").getMd5() != null) {
			throw new IllegalArgumentException("MD5 types are not allowed");
		}
		if (typeDefId.isAbsolute()) {
			return getJsonSchema(typeDefId.getTypeString(), true);
		} else {
			// resolveType populates the jsonschema cache as well as the type cache
			return getJsonSchema(resolveType(typeDefId), false);
		}
	}
	
	private static class GodDammitWrapper extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public GodDammitWrapper(final Throwable cause) {
			super(cause);
		}
	}
	
	/* NOTE FOR FUTURE ME (and other future devs)
	 * The way the two caches interact here is easy to screw up and add race conditions or
	 * outdated information to the cache if you're not careful
	 * If you're making changes keep race conditions in mind and make sure your changes won't
	 * cause the cache to be polluted with out of date values.
	 */
	
	private String resolveType(final TypeDefId type)
			throws NoSuchModuleException, NoSuchTypeException, TypeFetchException {
		try {
			return typeCache.get(
					type.getTypeString(),
					typestr -> {
						final TypeInfo ti = getTypeInfo(typestr);
						final byte[] jsonschema = ti.getJsonSchema().getBytes(UTF_8);
						getJsonSchema(ti.getTypeDef(), jsonschema); // populate jsonschema cache
						return ti.getTypeDef();
					});
		} catch (GodDammitWrapper e) {
			handleException(e);
			return null; // impossible to get here but codecov will whine just the same
		}
	}

	private ResolvedType getJsonSchema(final String absoluteType, final boolean updateTypeCache)
			throws TypeFetchException, NoSuchTypeException, NoSuchModuleException {
		final byte[] jsonSchema;
		try {
			jsonSchema = getJsonSchema(absoluteType, null);
		} catch (GodDammitWrapper e) {
			handleException(e);
			return null; // impossible to get here but codecov will whine just the same
		}
		if (updateTypeCache) {
			// We want to update the type cache *after* the json schema cache is populated,
			// otherwise there might be pointers in the type cache to things in the jsonschema
			// cache that haven't completed the write, although that'd take microsecond timing
			updateTypeCache(absoluteType);
		}
		return new ResolvedType(abstype(absoluteType), new String(jsonSchema, UTF_8));
	}
	
	private byte[] getJsonSchema(
			final String absoluteType,
			final byte[] incomingJSONSchema)
			throws GodDammitWrapper {
		return jsonSchemaCache.get(
				absoluteType,
				abstype -> {
					if (incomingJSONSchema != null) {
						return incomingJSONSchema;
					}
					final TypeInfo ti = getTypeInfo(abstype);
					return ti.getJsonSchema().getBytes(UTF_8);
				});
	}
	
	private AbsoluteTypeDefId abstype(final String absoluteTypeString) {
		return AbsoluteTypeDefId.fromAbsoluteTypeString(absoluteTypeString);
	}
	
	private void updateTypeCache(final String absoluteTypeString) {
		/* Only update the type cache if
		 * 1. There's already entries in the cache for this type
		 * 2. This type is newer than the entries
		 * The incoming absolute type may not be the most recent version of the type, so only
		 * update the cache if we know it's out of date. Otherwise let the cache loading
		 * mechanism do its thing, which *will* fetch the most recent version of the type.
		 * Adding a new entry here might add an entry pointing to a super old version of the type.
		 * 
		 * This prevents inconsistent results like getting a newer version of a type when
		 * specifying the absolute type than when specifying a non-absolute type, which should
		 * never happen. It does not ensure the type cache is completely up to date with the
		 * most recent version of the type.
		 */
		final AbsoluteTypeDefId absType = abstype(absoluteTypeString);
		final BiFunction<String, String, String> reMapFn = (key, oldvalue) ->
				gt(absType, abstype(oldvalue)) ? absoluteTypeString : oldvalue;
		
		final String major = new TypeDefId(absType.getType(), absType.getMajorVersion())
				.getTypeString();
		// e.g. Module.Type-3
		typeCache.asMap().computeIfPresent(major, reMapFn);
		// e.g. Module.Type
		typeCache.asMap().computeIfPresent(absType.getType().getTypeString(), reMapFn);
	}
	
	// no md5 types allowed here
	private boolean gt(final AbsoluteTypeDefId t1, AbsoluteTypeDefId t2) {
		// Ideally we'd implement a comparable in the type def ID class for this stuff but
		// MD5 types make that impossible, and the mixing of MD5 and semantic version types
		// is too baked into the type DB to split them into separate classes easily, which
		// is what should be done
		if (t1.getMajorVersion() > t2.getMajorVersion()) {
			return true;
		} else if (t1.getMajorVersion() == t2.getMajorVersion() &&
				t1.getMinorVersion() > t2.getMinorVersion()) {
			return true;
		}
		return false;
	}

	private void handleException(final GodDammitWrapper e)
			throws NoSuchModuleException, NoSuchTypeException, TypeFetchException {
		if (e.getCause() instanceof NoSuchModuleException) {
			throw (NoSuchModuleException) e.getCause();
		}
		if (e.getCause() instanceof NoSuchTypeException) {
			throw (NoSuchTypeException) e.getCause();
		}
		throw (TypeFetchException) e.getCause();
	}

	private TypeInfo getTypeInfo(final String type) throws GodDammitWrapper {
		try {
			return client.getTypeInfo(type);
		} catch (ServerException e) {
			if ( // error codes would be nice
					e.getMessage().contains("Module doesn't exist: ") ||
					e.getMessage().contains("was not initialized. For") ||
					e.getMessage().contains("Module wasn't uploaded: ")
					) {
				throw new GodDammitWrapper(new NoSuchModuleException(e.getMessage(), e));
			}
			if (
					e.getMessage().contains("This type wasn't released yet") ||
					e.getMessage().contains("Unable to locate type: ")
					) {
				throw new GodDammitWrapper(new NoSuchTypeException(e.getMessage(), e));
			}
			// ew. Don't want the SE data (e.g. server side stack trace) in the message, but
			// don't want to lose it either.
			// This is the best I could come up with, maybe there's a better way
			throw new GodDammitWrapper(new TypeFetchException(
					"Failed retrieving type info from remote type database: " + e.getMessage(),
					new TypeFetchException(e.getData(), e)));
		} catch (IOException | JsonClientException e) {
			throw new GodDammitWrapper(new TypeFetchException(
					"Failed retrieving type info from remote type database: " +
					e.getMessage(), e));
		}
	}

	/** Create a builder for a {@link DelegatingTypeProvider}.
	 * @param client a client pointing at the workspace to which type queries should be delegated.
	 * @return the builder.
	 */
	public static Builder getBuilder(final WorkspaceClient client) {
		return new Builder(client);
	}
	
	/** A builder for a {@link DelegatingTypeProvider}. */
	public static class Builder {
		
		private final WorkspaceClient client;
		private int typeCacheTimeMS = DEFAULT_TYPE_CACHE_TIME_MS;
		private int typeCacheMaxSize = DEFAULT_TYPE_CACHE_MAX_SIZE;
		private int jsonschemaCacheMaxSize = DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE;
		private Ticker cacheTicker = Ticker.systemTicker();
		private boolean evictionsOnCurrentThread = false;

		private Builder(final WorkspaceClient client) {
			this.client = requireNonNull(client, "client");
		}
		
		/** Set the amount of time an entry in the non-absolute to absolute type cache should
		 * remain in milliseconds. Typically is this a small amount of time so that type updates
		 * are reflected reasonably quickly.
		 * @param typeCacheTimeMS the cache expiration time in milliseconds defaulting to
		 * {@link DelegatingTypeProvider#DEFAULT_TYPE_CACHE_TIME_MS}.
		 * @return this builder.
		 */
		public Builder withTypeCacheTimeMS(final int typeCacheTimeMS) {
			this.typeCacheTimeMS = gt(typeCacheTimeMS, 0, "typeCacheTimeMS");
			return this;
		}
		
		/** Set the maximum size of the non-absolute to absolute type mapping cache in bytes.
		 * Mapping sizes are calculated as sum of the Java UTF-16 encoding size of the
		 * non-absolute type string and absolute type string. References, etc. are not
		 * accounted for.
		 * @param typeCacheMaxSize the maximum cache size, defaulting to
		 * {@link DelegatingTypeProvider#DEFAULT_TYPE_CACHE_MAX_SIZE}
		 * @return this builder.
		 */
		public Builder withTypeCacheMaxSize(final int typeCacheMaxSize) {
			this.typeCacheMaxSize = gt(typeCacheMaxSize, 0, "typeCacheMaxSize");
			return this;
		}

		/** Set the maximum size of the absolute type to JSONschema mapping cache in bytes.
		 * Mapping sizes are calculated as sum of the Java UTF-16 encoding size of the absolute
		 * type string and the size of the UTF-8 encoded JSON schema. References, etc. are not
		 * accounted for.
		 * 
		 * The size must be at least
		 * {@link DelegatingTypeProvider#DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE} as the caching
		 * logic depends on the jsonschema cache being populated.
		 * @param jsonschemaCacheMaxSize the maximum cache size, defaulting to
		 * {@link DelegatingTypeProvider#DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE}.
		 * @return this builder.
		 */
		public Builder withJsonSchemaCacheMaxSize(final int jsonschemaCacheMaxSize) {
			this.jsonschemaCacheMaxSize = gt(jsonschemaCacheMaxSize,
					DEFAULT_JSONSCHEMA_CACHE_MAX_SIZE, "jsonschemaCacheMaxSize");
			return this;
		}
		
		/** Set the ticker for the non-absolute to absolute type cache; generally only useful
		 * for testing purposes.
		 * @param ticker the ticker. Null input is silently ignored.
		 * @return this builder.
		 */
		public Builder withCacheTicker(final Ticker ticker) {
			if (ticker != null) {
				this.cacheTicker = ticker;
			}
			return this;
		}

		/** If true, cache evictions will be performed on the current thread rather than
		 * asynchronously. Only use this setting for testing.
		 * @param sync true to run cache evictions on the current thread. 
		 * @return this builder.
		 */
		public Builder withRunCacheEvictionsOnCurrentThread(final boolean sync) {
			this.evictionsOnCurrentThread = sync;
			return this;
		}
		
		private int gt(final int num, final int min, final String name) {
			if (num < min) {
				throw new IllegalArgumentException(name + " must be >= " + min);
			}
			return num;
		}
		
		/** Build the {@link DelegatingTypeProvider}.
		 * @return the type provider.
		 */
		public DelegatingTypeProvider build() {
			return new DelegatingTypeProvider(client, typeCacheTimeMS, typeCacheMaxSize,
					jsonschemaCacheMaxSize, cacheTicker, evictionsOnCurrentThread);
		}
	}
	
/* APPENDIX: Production info on JSONSchema sizes for cache sizing
 * Retrieved from KBase production on 2022/4/23
 * 
$ ipython
In [1]: from biokbase.workspace.client import Workspace
In [2]: wsurl = 'https://kbase.us/services/ws'
In [3]: ws = Workspace(wsurl)
In [4]: sizes = {}
In [5]: for mod in ws.list_all_types({}).keys():
   ...:     types = ws.get_all_type_info(mod)
   ...:     for typ in types:
   ...:         for typever in typ['type_vers']:
   ...:             print(typever)
   ...:             sizes[typever] = len(ws.get_jsonschema(typever))
   ...:
BAMBI.BambiRunResult-1.0

*snip*

ProbabilisticAnnotation.RxnProbs-1.0

In [6]: len(sizes)
Out[6]: 581

In [7]: sum(sizes.values())
Out[7]: 4198723

In [10]: import statistics as stats

In [11]: stats.mean(sizes.values())
Out[11]: 7226.717728055078

In [12]: stats.median(sizes.values())
Out[12]: 3764

In [13]: stats.pstdev(sizes.values())
Out[13]: 8793.704647534383

In [19]: max(sizes.items(), key=lambda i: i[1])
Out[19]: ('KBaseFBA.FBAModel-13.0', 58560)

In [20]: min(sizes.items(), key=lambda i: i[1])
Out[20]: ('KBaseNarrative.Metadata-1.0', 112)

In [23]: sum([len(k) for k in sizes.keys()])
Out[23]: 16744

In [24]: max([len(k) for k in sizes.keys()])
Out[24]: 51

In [25]: min([len(k) for k in sizes.keys()])
Out[25]: 14

 */

}
