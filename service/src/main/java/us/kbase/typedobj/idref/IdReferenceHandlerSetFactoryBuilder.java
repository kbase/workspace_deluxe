package us.kbase.typedobj.idref;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import us.kbase.auth.AuthToken;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.workspace.database.DependencyStatus;

/** A builder for a factory for a set of {@link IdReferenceHandler}s.
 * 
 * The reason for all this indirection is to allow the set of ID reference handlers to be
 * configured early in the build process of an application, thus allowing for dependency injection.
 * However, user credentials can not always be configured at the beginning of the process, so
 * the builder allows for creating the factory at a later stage. For example, in the case of a
 * server, the builder would probably be configured during the service build step, which
 * happens once, and the factory created once for each service call, using the user credentials
 * from that call. Finally, the factory can be used to create the actual set of handlers at
 * at later stage of the application (for example when control of the service call has been
 * handed off to an application library that has no knowledge of the service layer or user
 * credentials, and that later stage can configure the handlers using
 * {@link IdReferenceHandlerSetFactory#createHandlers(Class)} to determine what type of object will
 * be used to categorized IDs.
 * 
 * @see IdReferenceHandlerSetFactory
 * @see IdReferenceHandlerSet
 * @see IdReferenceHandlerFactory
 * @author gaprice@lbl.gov
 *
 */
public class IdReferenceHandlerSetFactoryBuilder {
	
	private final Map<IdReferenceType, IdReferenceHandlerFactory> factories; // tree map
	private final int maxUniqueIdCount;
	
	private IdReferenceHandlerSetFactoryBuilder(
			final Map<IdReferenceType, IdReferenceHandlerFactory> factories, // tree map
			final int maxUniqueIdCount) {
		this.factories = factories;
		this.maxUniqueIdCount = maxUniqueIdCount;
	}
	
	// deliberately not implementing hashcode & equals
	
	/** Get a handler factory from this builder.
	 * @param userToken the token for the user requesting processing of the IDs. The token
	 * may be used to lookup and/or process information in authenticated resources. The token
	 * may be null in the case where all the registered {@link IdReferenceHandlerFactory}s
	 * do not require a token (see {@link Builder#withFactory(IdReferenceHandlerFactory)}.
	 * @return a handler factory.
	 */
	public IdReferenceHandlerSetFactory getFactory(final AuthToken userToken) {
		return new IdReferenceHandlerSetFactory(maxUniqueIdCount, factories, userToken);
	}
	
	// TODO NAMING this is actually a general post processing mechanism, not just permissions.
	// Come up with a reasonable name and rename this stuff.
	/** Create a permission handler set that makes data associated with any
	 * IDs publicly readable.
	 * @return the new handler.
	 */
	public IdReferencePermissionHandlerSet createPermissionHandler() {
		final Map<IdReferenceType, IdReferencePermissionHandler> handlers = new HashMap<>();
		for (final IdReferenceType t: factories.keySet()) {
			handlers.put(t, factories.get(t).createPermissionHandler());
		}
		return new IdReferencePermissionHandlerSet(handlers);
	}
	
	/** Create a permission handler set.
	 * @param userName the user that will be granted permissions to the data associated with
	 * a set of IDs.
	 * @return the new handler.
	 */
	public IdReferencePermissionHandlerSet createPermissionHandler(final String userName) {
		requireNonNull(userName, "userName");
		final Map<IdReferenceType, IdReferencePermissionHandler> handlers = new HashMap<>();
		for (final IdReferenceType t: factories.keySet()) {
			handlers.put(t, factories.get(t).createPermissionHandler(userName));
		}
		return new IdReferencePermissionHandlerSet(handlers);
	}
	
	/** Get the status of any dependencies of the contained factories in this builder.
	 * @return the status of the factory dependencies.
	 */
	public List<DependencyStatus> getDependencyStatus() {
		return factories.keySet().stream()
				.flatMap(k -> factories.get(k).getDependencyStatus().stream())
				.collect(Collectors.toList());
		
	}

	/** Get a builder for a {@link IdReferenceHandlerSetFactoryBuilder}.
	 * @param maxUniqueIdCount - the maximum number of unique IDs allowed in
	 * this handler. The handler implementation defines what non-unique means,
	 * but generally the definition is that the IDs are associated with the
	 * same object and are the same ID.
	 * @return a new builder.
	 */
	public static final Builder getBuilder(final int maxUniqueIdCount) {
		return new Builder(maxUniqueIdCount);
	}
	
	/** A builder for a {@link IdReferenceHandlerSetFactoryBuilder}.
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class Builder {

		private final int maxUniqueIdCount;
		private final Map<IdReferenceType,IdReferenceHandlerFactory> factories = new TreeMap<>();

		private Builder(final int maxUniqueIdCount) {
			if (maxUniqueIdCount < 0) {
				throw new IllegalArgumentException(
						"maxUniqueIdCount must be at least 0");
			}
			this.maxUniqueIdCount = maxUniqueIdCount;
		}
		
		/** Add a factory to the builder. If the type of the factory is the same as the type
		 * of a previously added factory, it will overwrite the older factory.
		 * Factories can be added at a later stage of the build using
		 * {@link IdReferenceHandlerSetFactory#addFactory(IdReferenceHandlerFactory)}.
		 * @param factory the new factory.
		 * @return this builder.
		 */
		public Builder withFactory(final IdReferenceHandlerFactory factory) {
			if (factory == null) {
				throw new NullPointerException("factory cannot be null");
			}
			if (factory.getIDType() == null) {
				throw new NullPointerException("factory returned null for ID type");
			}
			factories.put(factory.getIDType(), factory);
			return this;
		}
		
		/** Build the factory builder.
		 * @return the new factory builder.
		 */
		public IdReferenceHandlerSetFactoryBuilder build() {
			return new IdReferenceHandlerSetFactoryBuilder(factories, maxUniqueIdCount);
		}
	}
	

}
