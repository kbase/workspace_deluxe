package us.kbase.typedobj.test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.idref.IdReferenceHandlers.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlers.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlersFactory.IdReferenceHandlerFactory;

public class DummyIdHandlerFactory<T> implements IdReferenceHandlerFactory<T> {

	private static class DummyIdHandler<T> implements IdReferenceHandler<T> {

		private final Set<String> foundIDs = new HashSet<String>();
		private final Map<String, String> idMapping;
		private boolean locked = false;

		public DummyIdHandler(Map<String, String> idMapping) {
			this.idMapping = idMapping;
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
	}


	private final Map<String, String> idMapping;

	public DummyIdHandlerFactory(Map<String, String> idMapping) {
		this.idMapping = idMapping;
	}

	@Override
	public IdReferenceHandler<T> createHandler() {
		return new DummyIdHandler<T>(idMapping);
	}

}
