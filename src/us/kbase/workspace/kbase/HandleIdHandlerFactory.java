package us.kbase.workspace.kbase;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.SimpleRemappedId;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.RemappedId;

public class HandleIdHandlerFactory implements IdReferenceHandlerFactory {

	//TODO 2 copy method needs to call exists on any handle IDs (needs get ext ids method)
	//TODO unit tests
	
	public static final IdReferenceType type = new IdReferenceType("handle");
	private final URL handleService;
	private final AuthToken userToken;
	
	/** pass in null for the handle service URL to cause an exception to be
	 * thrown if a handle id is encountered
	 */
	public HandleIdHandlerFactory(
			final URL handleServiceURL,
			final AuthToken userToken) {
		
		if (userToken == null) {
			throw new NullPointerException(
					"userToken cannot be null");
		}
		this.handleService = handleServiceURL;
		this.userToken = userToken;
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(Class<T> clazz) {
		return new HandleIdHandler<T>();
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}
	
	public class HandleIdHandler<T> extends IdReferenceHandler<T> {
		// seems like this might be a candidate for an abstract class, lock/processed/null checking common code

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		
		private HandleIdHandler() {}
		
		@Override
		protected boolean addIdImpl(final T associatedObject, final String id,
				final List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if (handleService == null) {
				throw new IdReferenceException("Found handle id " + id +
						". The workspace service currently does not have a " +
						"connection to the handle service and so cannot " +
						"process objects containing handle IDs.",
						type, associatedObject, "" + id,
						attributes, null);
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
		protected boolean addIdImpl(final T associatedObject, final Long id,
				final List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			throw new IdParseException(
					"Handle Service IDs are expected to be strings. Got: " +
							id, type, associatedObject, "" + id, attributes,
							null);
		}

		@Override
		protected void processIdsImpl() throws IdReferenceHandlerException {
			final Set<String> handles = new HashSet<String>();
			for (final Set<String> idset: ids.values()) {
				handles.addAll(idset);
			}
			if (handles.isEmpty()) {
				return;
			}
			if (handleService == null) {
				throw new IdReferenceHandlerException(
						"The workspace is not currently connected to the Handle Service and cannot process Handle ids.",
						type, null);
			}
			final Long allreadable;
			try {
				final AbstractHandleClient ahc = new AbstractHandleClient(
						handleService, userToken);
				if (handleService.getProtocol().equals("http")) {
					ahc.setIsInsecureHttpConnectionAllowed(true);
				}
				allreadable = ahc.areReadable(new LinkedList<String>(handles));
			} catch (UnauthorizedException e) {
				throw new IdReferenceHandlerException(
						"Authorization for Handle Service failed. The server said: "
								+ e.getLocalizedMessage(), type, e);
			} catch (IOException e) {
				throw new IdReferenceHandlerException(
						"There was a communication error while trying to contact the Handle Service: "
						+ e.getLocalizedMessage(), type, e);
			} catch (JsonClientException e) {
				throw new IdReferenceHandlerException(
						"There was an unexpected error while trying to contact the Handle Service: "
						+ e.getLocalizedMessage(), type, e);
			}
			//per Tom Brettin, 0 = false, anything else = true
			if (allreadable == 0) {
				throw new IdReferenceHandlerException(
						"The Handle Service reported that at least one of " +
						"the handles contained in the objects in this call " +
						"was not accessible with your credentials. The call " +
						"cannot complete.", type, null);
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
			throw new NoSuchIdException("No such ID contained in this mapper: "
					+ oldId);
		}

		@Override
		protected Set<RemappedId> getRemappedIdsImpl(T associatedObject) {
			final Set<RemappedId> newids = new HashSet<RemappedId>();
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
