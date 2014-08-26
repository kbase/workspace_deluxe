package us.kbase.typedobj.idref;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;

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
	
	//TODO unit tests

	private final Map<IdReferenceType,IdReferenceHandlerFactory> factories = 
			new HashMap<IdReferenceType,IdReferenceHandlerFactory>();
	private final int maxUniqueIdCount;
	
	/** An interface for a factory that creates an ID handler.
	 * @author gaprice@lbl.gov
	 */
	public interface IdReferenceHandlerFactory {
		/** Create a empty, unlocked ID handler from this factory.
		 * @param clazz the class of object to associate with IDs in the
		 * produced ID handler.
		 * @return an ID hander.
		 */
		public <T> IdReferenceHandler<T> createHandler(final Class<T> clazz);
		public IdReferenceType getIDType();
	}
	
	/** Create a handler set factory.
	 * @param maxUniqueIdCount - the maximum number of unique IDs allowed in
	 * this handler. The handler implementation defines what non-unique means,
	 * but generally the definition is that the IDs are associated with the
	 * same object and are the same ID.
	 */
	public IdReferenceHandlerSetFactory(final int maxUniqueIdCount) {
		if (maxUniqueIdCount < 0) {
			throw new IllegalArgumentException(
					"maxUniqueIdCount must be at least 0");
		}
		this.maxUniqueIdCount = maxUniqueIdCount;
	}
	
	/** Add a factory to this factory set.
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
		for (final Entry<IdReferenceType, IdReferenceHandlerFactory> e:
				factories.entrySet()) {
			handlers.put(e.getKey(), e.getValue().createHandler(clazz));
		}
		return new IdReferenceHandlerSet<T>(maxUniqueIdCount, handlers);
	}
	
}
