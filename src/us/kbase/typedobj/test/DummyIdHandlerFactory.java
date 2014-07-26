package us.kbase.typedobj.test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.DefaultRemappedId;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;

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
		public RemappedId getRemappedId(String oldId) {
			if (oldId == null) {
				throw new NullPointerException();
			}
			if (!foundIDs.contains(oldId)) {
				throw new IllegalArgumentException("ID not in object: " + oldId);
			}
			if (!idMapping.containsKey(oldId)) {
				throw new IllegalArgumentException("No mapping for ID: " + oldId);
			}
			return new DefaultRemappedId(idMapping.get(oldId));
		}

		@Override
		public void lock() {
			locked = true;
		}

		@Override
		public IdReferenceType getIdType() {
			return type;
		}

		@Override
		public Set<RemappedId> getRemappedIds(T associatedObject) {
			throw new UnimplementedException();
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
