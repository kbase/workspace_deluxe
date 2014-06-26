package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class IdReferenceHandlers {
	
	//TODO unit tests
	//TODO 1 test extraction of various types
	
	private final int maxUniqueIdCount;
	private int currentUniqueIdCount = 0;
	private boolean locked = false;
	private boolean processed = false;
	
	private final Map<IdReferenceType, IdReferenceHandler> handlers;
	
	/** A handler for typed object IDs. Responsible for checking the
	 * syntax of the id and its attributes, and remapping IDs if necessary.
	 * @author gaprice@lbl.gov
	 *
	 */
	public interface IdReferenceHandler {
		
		/** Add an id to the handler
		 * @param id the id.
		 * @return boolean if this is a unique ID stored in memory and thus
		 * should count towards the maximum ID limit.
		 * @throws IdReferenceHandlerException if the ID could not be added.
		 * @throws HandlerLockedException if the handler is already locked.
		 */
		public boolean addId(String id, List<String> attributes)
				throws IdReferenceHandlerException,
				HandlerLockedException;
		/** Perform any necessary batch processing of the IDs before
		 * remapping and locks the handler.
		 */
		public void processIds() throws IdReferenceHandlerException;
		/** Translate an ID to the remapped ID.
		 * @param oldId the original ID.
		 * @return the new, remapped ID.
		 */
		public String getRemappedId(String oldId) throws NoSuchIdException;
		/** Prevent addition of any more IDs.
		 */
		public void lock();
	}
	
	protected IdReferenceHandlers(final int maxUniqueIdCount,
			final Map<IdReferenceType, IdReferenceHandler> handlers) {
		this.maxUniqueIdCount = maxUniqueIdCount;
		this.handlers = new HashMap<IdReferenceType,
				IdReferenceHandlers.IdReferenceHandler>(handlers);
	}

	public boolean hasHandler(final IdReferenceType idType) {
		return handlers.containsKey(idType);
	}
	
	/** Add an ID to the appropriate ID handler.
	 * @param id the new ID.
	 * @throws TooManyIdsException if too many IDs are currently in memory.
	 * @throws IdReferenceHandlerException if the id could not be handled
	 */
	public void addId(final IdReference id)
			throws TooManyIdsException, IdReferenceHandlerException {
		if (locked) {
			throw new IllegalStateException(
					"This ID handlers instance is locked");
		}
		if (id == null) {
			throw new NullPointerException("id cannot be null");
		}
		if (!handlers.containsKey(id.getType())) {
			throw new NoSuchIdReferenceHandlerException(
					"There is no handler for the ID type " +
							id.getType().getType());
		}
		final boolean newId = handlers.get(id.getType()).addId(
				id.getId(), id.getAttributes());
		currentUniqueIdCount += newId ? 1 : 0;
		if (currentUniqueIdCount > maxUniqueIdCount) {
			throw new TooManyIdsException("Maximum ID count of " + 
					maxUniqueIdCount + " exceeded");
		}
	}
	
	/** Process all the IDs saved in all the registered handlers and locks
	 * the handlers. Calling this methond twice will have no effect.
	 * @throws IdReferenceHandlerException if there was an error processing
	 * the IDs.
	 * 
	 */
	public void processIDs() throws IdReferenceHandlerException {
		if (processed) {
			return;
		}
		locked = true;
		processed = true;
		for (final Entry<IdReferenceType, IdReferenceHandler> es:
			handlers.entrySet()) {
			es.getValue().processIds();
			es.getValue().lock();
		}
	}
	
	/** Check if processIds() has been called on this handler. Implies
	 * that the handler is locked.
	 * @return true if processIds() has been called.
	 */
	public boolean wereIdsProcessed() {
		return processed;
	}
	
	/** Translate an ID to the remapped ID.
	 * @param idType the ID type.
	 * @param oldId the original ID.
	 * @return the new, remapped ID.
	 */
	public String getRemappedId(
			final IdReferenceType idType,
			final String oldId) {
		if (idType == null || oldId == null) {
			throw new NullPointerException("idType and oldId can't be null");
		}
		if (!handlers.containsKey(idType)) {
			throw new NoSuchIdReferenceHandlerException(
					"There is no handler registered for the ID type " + 
					idType.getType());
		}
		return handlers.get(idType).getRemappedId(oldId);
	}
	
	public IdReferenceHandlers lock() {
		locked = true;
		for (final Entry<IdReferenceType, IdReferenceHandler> es:
			handlers.entrySet()) {
			es.getValue().lock();
		}
		return this;
	}
	
	public int size() {
		return currentUniqueIdCount;
	}
	
	public boolean isEmpty() {
		return currentUniqueIdCount == 0;
	}
	
	@SuppressWarnings("serial")
	public class TooManyIdsException extends Exception {

		public TooManyIdsException(final String message) {
			super(message);
		}
	}
	
	@SuppressWarnings("serial")
	public class NoSuchIdReferenceHandlerException extends RuntimeException {

		public NoSuchIdReferenceHandlerException(String message) {
			super(message);
		}
		
	}
	
	@SuppressWarnings("serial")
	public class NoSuchIdException extends RuntimeException {

		public NoSuchIdException(String message) {
			super(message);
		}
		
	}
	
	@SuppressWarnings("serial")
	public class HandlerLockedException extends RuntimeException {
		
		public HandlerLockedException(String message) {
			super(message);
		}
	}
	
	@SuppressWarnings("serial")
	public class IdReferenceHandlerException extends Exception {
		
		private final String id;
		private final IdReferenceType idType;
		private final List<String> idAttributes;
		
		public IdReferenceHandlerException(
				final String message,
				final String id,
				final IdReferenceType idType,
				final List<String> idAttributes,
				final Throwable cause) {
			super(message, cause);
			if (message == null || id == null || idType == null ||
					idAttributes == null || cause == null) {
				throw new NullPointerException("No arguments can be null");
			}
			this.id = id;
			this.idType = idType;
			this.idAttributes = Collections.unmodifiableList(
					new LinkedList<String>(idAttributes));
		}

		public String getId() {
			return id;
		}

		public IdReferenceType getIdType() {
			return idType;
		}

		public List<String> getIdAttributes() {
			return idAttributes;
		}
		
	}
}
