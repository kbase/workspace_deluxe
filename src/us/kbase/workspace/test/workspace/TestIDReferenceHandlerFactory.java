package us.kbase.workspace.test.workspace;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.typedobj.idref.SimpleRemappedId;

public class TestIDReferenceHandlerFactory implements IdReferenceHandlerFactory {

	//TODO 1 exercise all these errors
	
	private final IdReferenceType type;
	
	public TestIDReferenceHandlerFactory(final IdReferenceType type) {
		this.type = type;
	}

	@Override
	public <T> IdReferenceHandler<T> createHandler(Class<T> clazz) {
		return new TestIDReferenceHandler<T>();
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}
	
	public class TestIDReferenceHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		
		private IdParseException parseExcept = null;
		private IdReferenceException refExcept = null;
		private IdReferenceHandlerException genExcept = null;
		
		private TestIDReferenceHandler() {
		}
		
//		@Override
//		protected boolean addIdImpl(T associatedObject,
//				Long id, List<String> attributes)
//				throws IdReferenceHandlerException, HandlerLockedException {
//			return addId(associatedObject, "" + id, attributes);
//		}
		
		@Override
		protected boolean addIdImpl(T associatedObject,
				String id, List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if ("parseExcept".equals(id)) {
				throw new IdParseException("Parse exception for ID " + id,
						type, associatedObject, id, attributes, null);
			}
			if ("refExcept".equals(id)) {
				throw new IdReferenceException(
						"Reference exception for ID " + id, type,
						associatedObject, id, attributes, null);
			}
			if ("genExcept".equals(id)) {
				throw new IdReferenceHandlerException(
						"General exception for ID " + id, type, null);
			}
			if ("procParseExcept".equals(id)) {
				parseExcept = new IdParseException("Parse exception for ID " + id,
						type, associatedObject, id, attributes, null);
			}
			if ("procRefExcept".equals(id)) {
				refExcept = new IdReferenceException(
						"Reference exception for ID " + id, type,
						associatedObject, id, attributes, null);
			}
			if ("procGenExcept".equals(id)) {
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
		protected void processIdsImpl() throws IdReferenceHandlerException {
			for (IdReferenceHandlerException e:
					Arrays.asList(parseExcept, refExcept, genExcept)) {
				if (e != null) {
					throw e;
				}
			}
			
		}

		@Override
		protected RemappedId getRemappedIdImpl(String oldId)
				throws NoSuchIdException {
			for (final T assobj: ids.keySet()) {
				if (ids.get(assobj).contains(oldId)) {
					return new SimpleRemappedId(oldId);
				}
			}
			throw new NoSuchIdException(oldId);
		}

		@Override
		protected Set<RemappedId> getRemappedIdsImpl(T associatedObject) {
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
		public IdReferenceType getIdType() {
			return type;
		}
		
	}

}
