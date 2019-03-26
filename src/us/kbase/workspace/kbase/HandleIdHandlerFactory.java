package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.abstracthandle.AbstractHandleClient;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.HandlerLockedException;
//import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.SimpleRemappedId;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.typedobj.idref.RemappedId;

public class HandleIdHandlerFactory implements IdReferenceHandlerFactory {

	//TODO TEST unit tests
	//TODO JAVADOC
	
	public static final IdReferenceType type = new IdReferenceType("handle");
	private final URL handleService;
	private final HandleMngrClient client;
	
	/** pass in null for the handle service URL to cause an exception to be
	 * thrown if a handle id is encountered. Same for the client.
	 */
	public HandleIdHandlerFactory(final URL handleServiceURL, final HandleMngrClient client) {
		this.handleService = handleServiceURL;
		this.client = client;
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(
			final Class<T> clazz,
			final AuthToken userToken) {
		return new HandleIdHandler<T>(requireNonNull(userToken, "userToken"));
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler() {
		return new HandlePermissionsHandler(null);
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler(final String userName) {
		return new HandlePermissionsHandler(userName);
	}

	@Override
	public IdReferenceType getIDType() {
		return type;
	}
	
	private class HandlePermissionsHandler implements IdReferencePermissionHandler {
		
		private final String user;

		private HandlePermissionsHandler(final String userName) {
			this.user = userName;
		}

		@Override
		public void addReadPermission(final Collection<String> ids)
				throws IdReferencePermissionHandlerException {
			if (ids == null || ids.isEmpty()) {
				return;
			}
			if (client == null) {
				throw new IdReferencePermissionHandlerException(
						"The workspace is not currently connected to the Handle Service " +
						"and cannot process Handle ids.");
			}
			try {
				if (user == null) {
					client.setPublicRead(new LinkedList<>(ids));
				} else {
					client.addReadAcl(new LinkedList<>(ids), user);
				}
			} catch (IOException e) {
				throw new IdReferencePermissionHandlerException(
						"There was an IO problem while attempting to set " +
								"Handle ACLs: " + e.getMessage(), e);
			} catch (UnauthorizedException e) {
				throw new IdReferencePermissionHandlerException(
						"Unable to contact the Handle Manager - " +
								"the Workspace credentials were rejected: " +
								e.getMessage(), e);
			} catch (ServerException e) {
				throw new IdReferencePermissionHandlerException(
						"The Handle Manager reported a problem while attempting " +
								"to set Handle ACLs: " + e.getMessage(), e);
			} catch (JsonClientException e) {
				throw new IdReferencePermissionHandlerException(
						"There was an unexpected problem while contacting the " +
								"Handle Manager to set Handle ACLs: " +
								e.getMessage(), e);
			}
		}
	}
	
	public class HandleIdHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private final AuthToken userToken;
		
		private HandleIdHandler(final AuthToken userToken) {
			this.userToken = userToken;
		}
		
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
		
//		@Override
//		protected boolean addIdImpl(final T associatedObject, final Long id,
//				final List<String> attributes)
//				throws IdReferenceHandlerException, HandlerLockedException {
//			throw new IdParseException(
//					"Handle Service IDs are expected to be strings. Got: " +
//							id, type, associatedObject, "" + id, attributes,
//							null);
//		}

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
				allreadable = ahc.isOwner(new LinkedList<String>(handles));
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
						"is not accessible - it may not exist, or the " +
						"supplied credentials may not own the node, or some " +
						"other reason. The call cannot complete.", type, null);
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
