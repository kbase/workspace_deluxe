package us.kbase.workspace.kbase;

import java.net.URL;
import java.util.List;
import java.util.Set;

import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.RemappedId;

public class HandleIdHandlerFactory implements IdReferenceHandlerFactory {

	private static final IdReferenceType type = new IdReferenceType("handle");
	
	public HandleIdHandlerFactory(
			final URL handleServiceURL,
			//TODO 1 add Refreshing token
			final URL handleManagerURL) {
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(Class<T> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}
	
	public class HandleIdHandler<T> implements IdReferenceHandler<T> {

		@Override
		public boolean addId(T associatedObject, String id,
				List<String> attributes) throws IdReferenceHandlerException,
				HandlerLockedException {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void processIds() throws IdReferenceHandlerException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public RemappedId getRemappedId(String oldId) throws NoSuchIdException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Set<RemappedId> getRemappedIds(T associatedObject) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void lock() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public IdReferenceType getIdType() {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
