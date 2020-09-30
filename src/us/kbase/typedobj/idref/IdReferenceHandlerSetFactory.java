package us.kbase.typedobj.idref;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import us.kbase.auth.AuthToken;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.workspace.database.DependencyStatus;

/** Builds a set of ID handlers for handling IDs found while validating a
 * typed object. The handler could, for example, check the ID format, check
 * that the ID refers to a valid item in a database, or both.
 * 
 * The handler builder is expected to build a set of empty handlers that are
 * ready to process IDs.
 * 
 * ID handlers allow associating IDs with a particular object. This is useful
 * for batch processing of typed objects, as the IDs can be associated with
 * a particular object but the entire ID set can be processed as a batch.
 * @author gaprice@lbl.gov
 *
 */
public class IdReferenceHandlerSetFactory {
	
	//TODO TEST unit tests

	private final Map<IdReferenceType, IdReferenceHandlerFactory> factories;
	private final int maxUniqueIdCount;
	private final AuthToken userToken;
	
	/** An interface for a factory that creates an ID handler.
	 * @author gaprice@lbl.gov
	 */
	public interface IdReferenceHandlerFactory {
		/** Create a empty, unlocked ID handler from this factory.
		 * @param clazz the class of object to associate with IDs in the
		 * produced ID handler.
		 * @param userToken the token for the user requesting processing of the IDs. The token
		 * may be used to lookup and/or process information in authenticated resources. The token
		 * may be null in the case where all the registered {@link IdReferenceHandlerFactory}s
		 * do not require a token.
		 * @return an ID hander.
		 */
		public <T> IdReferenceHandler<T> createHandler(
				final Class<T> clazz,
				final AuthToken userToken);
		
		/** Create a permission handler for the ID type that makes data associated with any
		 * IDs publicly readable.
		 * @return the new handler.
		 */
		public IdReferencePermissionHandler createPermissionHandler();
		
		/** Create a permission handler for the ID type.
		 * @param userName the user that will be granted permissions to the data associated with
		 * a set of IDs.
		 * @return the new handler.
		 */
		public IdReferencePermissionHandler createPermissionHandler(final String userName);
		
		/** Get the type of IDs this factory supports.
		 * @return the ID type.
		 */
		public IdReferenceType getIDType();
		
		/** Get the status of any dependencies on which this factory relies.
		 * @return the dependency statuses or an empty list if the factory has no dependencies or
		 *  is inactive.
		 */
		public List<DependencyStatus> getDependencyStatus();
	}
	
	/** Create a handler set factory.
	 * @param maxUniqueIdCount - the maximum number of unique IDs allowed in
	 * this handler. The handler implementation defines what non-unique means,
	 * but generally the definition is that the IDs are associated with the
	 * same object and are the same ID.
	 * @param factories the set of factories for handling ID types.
	 * @param userToken the token for the user requesting processing of the IDs. The token
	 * may be used to lookup and/or process information in authenticated resources. The token
	 * may be null in the case where all the registered {@link IdReferenceHandlerFactory}s
	 * do not require a token.
	 */
	IdReferenceHandlerSetFactory(
			final int maxUniqueIdCount,
			final Map<IdReferenceType, IdReferenceHandlerFactory> factories,
			final AuthToken userToken) {
		if (maxUniqueIdCount < 0) {
			throw new IllegalArgumentException(
					"maxUniqueIdCount must be at least 0");
		}
		this.userToken = userToken;
		this.maxUniqueIdCount = maxUniqueIdCount;
		this.factories = new HashMap<>(factories);
	}
	
	/** Add a factory to this factory set. If the type of the factory is the same as the type
	 * of a previously added factory, it will overwrite the older factory, including factories
	 * added via
	 * {@link IdReferenceHandlerSetFactoryBuilder.Builder#withFactory(IdReferenceHandlerFactory)}.
	 * @param factory the factory to add.
	 * @return this.
	 */
	public IdReferenceHandlerSetFactory addFactory(
			final IdReferenceHandlerFactory factory) {
		if (factory == null) {
			throw new NullPointerException("factory cannot be null");
		}
		if (factory.getIDType() == null) {
			throw new NullPointerException(
					"factory returned null for ID type");
		}
		factories.put(factory.getIDType(), factory);
		return this;
	}
	
	/** Create a set of ID handlers from this factory set.
	 * @param clazz the class of object to associate with IDs.
	 * @return the set of ID handlers.
	 */
	public <T> IdReferenceHandlerSet<T> createHandlers(final Class<T> clazz) {
		final Map<IdReferenceType, IdReferenceHandler<T>> handlers =
				new HashMap<IdReferenceType, IdReferenceHandler<T>>();
		for (final Entry<IdReferenceType, IdReferenceHandlerFactory> e: factories.entrySet()) {
			handlers.put(e.getKey(), e.getValue().createHandler(clazz, userToken));
		}
		return new IdReferenceHandlerSet<T>(maxUniqueIdCount, handlers);
	}
	
}
