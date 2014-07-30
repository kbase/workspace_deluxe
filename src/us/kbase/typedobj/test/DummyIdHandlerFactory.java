package us.kbase.typedobj.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.DefaultRemappedId;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.typedobj.util.Counter;

public class DummyIdHandlerFactory implements IdReferenceHandlerFactory {

	public static class DummyIdHandler<T> implements IdReferenceHandler<T> {

		private final Map<String, Counter> foundIDs = new HashMap<String, Counter>();
		private final Map<String, String> idMapping;
		private boolean locked = false;
		private IdReferenceType type;
		
		private final Map<String, Integer> userFoundIDs; 

		private DummyIdHandler(IdReferenceType type, Map<String, String> idMapping,
				Map<String, Integer> foundIDs) {
			this.idMapping = idMapping;
			this.type = type;
			this.userFoundIDs = foundIDs;
		}

		@Override
		public boolean addId(T associatedObject, Long id,
				List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			return addId(associatedObject, "" + id, attributes);
		}
		
		@Override
		public boolean addId(T associatedObject, String id,
				List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if (locked) {
				throw new IllegalArgumentException("locked");
			}
			//in a real implementation should check type is ok & for NPEs
			if (foundIDs.containsKey(id)) {
				foundIDs.get(id).increment();
				return false;
			}
			foundIDs.put(id, new Counter(1));
			return true;
		}

		@Override
		public void processIds() throws IdReferenceHandlerException {
			for (Entry<String, Counter> e: foundIDs.entrySet()) {
				userFoundIDs.put(e.getKey(), e.getValue().getValue());
			}
		}

		@Override
		public RemappedId getRemappedId(String oldId) {
			if (oldId == null) {
				throw new NullPointerException();
			}
			if (!foundIDs.containsKey(oldId)) {
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
		
		public Map<String, Integer> getFoundIDs() {
			Map<String, Integer> ret = new HashMap<String, Integer>();
			for (Entry<String, Counter> e: foundIDs.entrySet()) {
				ret.put(e.getKey(), e.getValue().getValue());
			}
			return ret;
		}
	}


	private final Map<String, String> idMapping;
	private final IdReferenceType type;
	private final Map<String, Integer> foundIds;

	public DummyIdHandlerFactory(IdReferenceType type, Map<String, String> idMapping,
			Map<String, Integer> foundIds) {
		this.idMapping = idMapping;
		this.type = type;
		this.foundIds = foundIds;
	}
	
	public DummyIdHandlerFactory(IdReferenceType type, Map<String, String> idMapping) {
		this.idMapping = idMapping;
		this.type = type;
		this.foundIds = new HashMap<String, Integer>();
	}

	@Override
	public <T> IdReferenceHandler<T> createHandler(final Class<T> clazz) {
		return new DummyIdHandler<T>(type, idMapping, foundIds);
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}

}
