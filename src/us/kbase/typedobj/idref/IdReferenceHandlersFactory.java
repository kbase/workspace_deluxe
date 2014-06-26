package us.kbase.typedobj.idref;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandler;

public class IdReferenceHandlersFactory {

	private final Map<IdReferenceType,IdReferenceHandlerFactory> factories = 
			new HashMap<IdReferenceType,IdReferenceHandlerFactory>();
	private final int maxUniqueIdCount;
	
	/** An interface for a factory that creates an ID handler.
	 * @author gaprice@lbl.gov
	 */
	public interface IdReferenceHandlerFactory {
		/** Create a empty, unlocked ID handler from this factory.
		 * @return an ID hander.
		 */
		public IdReferenceHandler createHandler();
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
			final IdReferenceHandlerFactory factory) {
		if (factory == null || idType == null) {
			throw new NullPointerException("factory and idType cannot be null");
		}
		factories.put(idType, factory);
	}
	
	public IdReferenceHandlers createHandlers() {
		final Map<IdReferenceType, IdReferenceHandler> handlers =
				new HashMap<IdReferenceType, IdReferenceHandler>();
		for (final Entry<IdReferenceType, IdReferenceHandlerFactory> e:
				factories.entrySet()) {
			handlers.put(e.getKey(), e.getValue().createHandler());
		}
		return new IdReferenceHandlers(maxUniqueIdCount, handlers);
	}
	
}
