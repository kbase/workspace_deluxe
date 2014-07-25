package us.kbase.workspace.test.workspace;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.idref.IdReferenceHandlers.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlers.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlersFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.typedobj.idref.SimpleRemappedId;

public class TestIDReferenceHandlerFactory implements IdReferenceHandlerFactory {

	//TODO 2 exercise all these errors
	
	private final IdReferenceType type;
	
	public TestIDReferenceHandlerFactory(final IdReferenceType type) {
		this.type = type;
	}

	@Override
	public <T> IdReferenceHandler<T> createHandler(Class<T> clazz) {
		return new TestIDReferenceHandler<T>(type);
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}
	
	public class TestIDReferenceHandler<T> implements IdReferenceHandler<T> {

		private final IdReferenceType type;
		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private boolean processed = false;
		private boolean locked = false;
		
		private IdParseException parseExcept = null;
		private IdReferenceException refExcept = null;
		private IdReferenceHandlerException genExcept = null;
		
		private TestIDReferenceHandler(final IdReferenceType type) {
			this.type = type;
		}
		
		@Override
		public boolean addId(T associatedObject,
				String id, List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if (locked) {
				throw new HandlerLockedException("This handler is locked");
			}
			if (associatedObject == null) {
				throw new NullPointerException(
						"associatedObject cannot be null");
			}
			if (id == null || id.isEmpty()) {
				throw new IdParseException(
						"IDs may not be null or the empty string",
						getIdType(), associatedObject, id, attributes, null);
			}
			if (attributes.contains("parseExcept")) {
				throw new IdParseException("Parse exception for ID " + id,
						type, associatedObject, id, attributes, null);
			}
			if (attributes.contains("refExcept")) {
				throw new IdReferenceException(
						"Reference exception for ID " + id, type,
						associatedObject, id, attributes, null);
			}
			if (attributes.contains("genExcept")) {
				throw new IdReferenceHandlerException(
						"General exception for ID " + id, type, null);
			}
			if (attributes.contains("procParseExcept")) {
				parseExcept = new IdParseException("Parse exception for ID " + id,
						type, associatedObject, id, attributes, null);
			}
			if (attributes.contains("procRefExcept")) {
				refExcept = new IdReferenceException(
						"Reference exception for ID " + id, type,
						associatedObject, id, attributes, null);
			}
			if (attributes.contains("procGenExcept")) {
				genExcept = new IdReferenceHandlerException(
						"General exception for ID " + id, type, null);
			}
			boolean unique = true;
			if (!ids.containsKey(associatedObject)) {
				ids.put(associatedObject, new HashSet<String>());
			}
			if (ids.get(associatedObject).contains(id)) {
				unique = false;
			} else {
				ids.get(associatedObject).add(id);
			}
			return unique;
		}

		@Override
		public void processIds() throws IdReferenceHandlerException {
			processed = true;
			locked = true;
			for (IdReferenceHandlerException e:
					Arrays.asList(parseExcept, refExcept, genExcept)) {
				if (e != null) {
					throw e;
				}
			}
			
		}

		@Override
		public RemappedId getRemappedId(String oldId) throws NoSuchIdException {
			for (final T assobj: ids.keySet()) {
				if (ids.get(assobj).contains(oldId)) {
					return new SimpleRemappedId(oldId);
				}
			}
			throw new NoSuchIdException(oldId);
		}

		@Override
		public Set<RemappedId> getRemappedIds(T associatedObject) {
			if (!processed) {
				throw new IllegalStateException(
						"IDs haven't been processed yet");
			}
			Set<RemappedId> newids = new HashSet<RemappedId>();
			if (!ids.containsKey(associatedObject)) {
				return newids;
			}
			for (final String id: ids.get(associatedObject)) {
				newids.add(new SimpleRemappedId(id));
			}
			return newids;
		}

		@Override
		public void lock() {
			locked = true;
		}

		@Override
		public IdReferenceType getIdType() {
			return type;
		}
		
	}

}
