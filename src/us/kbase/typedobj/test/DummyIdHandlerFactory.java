package us.kbase.typedobj.test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.idref.IdReferenceHandlers.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlersFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferenceType;

public class DummyIdHandlerFactory implements IdReferenceHandlerFactory {

	private static class DummyIdHandler<T> implements IdReferenceHandler<T> {

		private final Set<String> foundIDs = new HashSet<String>();
		private final Map<String, String> idMapping;
		private boolean locked = false;
		private IdReferenceType type;

		public DummyIdHandler(IdReferenceType type, Map<String, String> idMapping) {
			this.idMapping = idMapping;
			this.type = type;
		}

		@Override
		public boolean addId(T associatedObject, String id,
				List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if (locked) {
				throw new IllegalArgumentException("locked");
			}
			//in a real implementation should check type is ok & for NPEs
			if (foundIDs.contains(id)) {
				return false;
			}
			foundIDs.add(id);
			return true;
		}

		@Override
		public void processIds() throws IdReferenceHandlerException {
			// do nothing
		}

		@Override
		public String getRemappedId(String oldId) {
			if (oldId == null) {
				throw new NullPointerException();
			}
			if (!foundIDs.contains(oldId)) {
				throw new IllegalArgumentException("ID not in object: " + oldId);
			}
			if (!idMapping.containsKey(oldId)) {
				throw new IllegalArgumentException("No mapping for ID: " + oldId);
			}
			return idMapping.get(oldId);
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


	private final Map<String, String> idMapping;
	private final IdReferenceType type;

	public DummyIdHandlerFactory(IdReferenceType type, Map<String, String> idMapping) {
		this.idMapping = idMapping;
		this.type = type;
	}

	@Override
	public <T> IdReferenceHandler<T> createHandler(final Class<T> clazz) {
		return new DummyIdHandler<T>(type, idMapping);
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}

}
