package us.kbase.typedobj.idref;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandler;

/** Builds a set of ID handlers for handling IDs found while validating a
 * typed object. The handler could, for example, check the ID format, check
 * that the ID refers to a valid item in a database, or both.
 * 
 * ID handlers allow associating IDs with a particular object. This is useful
 * for batch processing of typed object, as the IDs can be associated with
 * a particular object but the entire ID set can be processed as a batch.
 * @author gaprice@lbl.gov
 *
 * @param <T> the type of the object to be associated with IDs.
 */
public class IdReferenceHandlersFactory<T> {

	private final Map<IdReferenceType,IdReferenceHandlerFactory<T>> factories = 
			new HashMap<IdReferenceType,IdReferenceHandlerFactory<T>>();
	private final int maxUniqueIdCount;
	
	/** An interface for a factory that creates an ID handler.
	 * @author gaprice@lbl.gov
	 */
	public interface IdReferenceHandlerFactory<T> {
		/** Create a empty, unlocked ID handler from this factory.
		 * @return an ID hander.
		 */
		public IdReferenceHandler<T> createHandler();
	}
	
	public IdReferenceHandlersFactory(final int maxUniqueIdCount) {
		if (maxUniqueIdCount < 0) {
			throw new IllegalArgumentException(
					"maxUniqueIdCount must be at least 0");
		}
		this.maxUniqueIdCount = maxUniqueIdCount;
	}
	
	public void addFactory(
			final IdReferenceType idType,
			final IdReferenceHandlerFactory<T> factory) {
		if (factory == null || idType == null) {
			throw new NullPointerException("factory and idType cannot be null");
		}
		factories.put(idType, factory);
	}
	
	public IdReferenceHandlers<T> createHandlers() {
		final Map<IdReferenceType, IdReferenceHandler<T>> handlers =
				new HashMap<IdReferenceType, IdReferenceHandler<T>>();
		for (final Entry<IdReferenceType, IdReferenceHandlerFactory<T>> e:
				factories.entrySet()) {
			handlers.put(e.getKey(), e.getValue().createHandler());
		}
		return new IdReferenceHandlers<T>(maxUniqueIdCount, handlers);
	}
	
}
