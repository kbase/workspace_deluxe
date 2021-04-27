package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.typedobj.idref.RemappedId;

/**
 * A factory for building a handler for Handle Service IDs. These are embedded in Workspace
 * Service objects and denoted in the object type specification with an @id handle annotation.
 */
public class HandleIdHandlerFactory implements IdReferenceHandlerFactory {

	//TODO TEST unit tests for HandleIDHandler
	
	public static final IdReferenceType TYPE = new IdReferenceType("handle");
	private final AbstractHandleClient client;
	
	/** Create the Handle ID handler factory.
	 * @param client a handle service client with administrator permissions. Must be able
	 * to set user permissions. Pass null if there is no handle service available - in this
	 * case an error will be throw if a handle ID is encountered.
	 */
	public HandleIdHandlerFactory(final AbstractHandleClient client) {
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
		return TYPE;
	}
	
	@Override
	public List<DependencyStatus> getDependencyStatus() {
		if (client == null) {
			return Collections.emptyList();
		}
		try {
			final String ver = (String) client.status().get("version");
			// no need to check return value, always returns OK or fails
			return Arrays.asList(new DependencyStatus(true, "OK", "Handle service", ver));
		} catch (IOException | JsonClientException e) {
			return Arrays.asList(
					new DependencyStatus(false, e.getMessage(), "Handle service", "Unknown"));
		}
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
	
	private class HandleIdHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private final AuthToken userToken;
		
		private HandleIdHandler(final AuthToken userToken) {
			this.userToken = userToken;
		}
		
		@Override
		protected boolean addIdImpl(final T associatedObject, final String id,
				final List<String> attributes)
				throws IdReferenceHandlerException, HandlerLockedException {
			if (client == null) {
				throw new IdReferenceException("Found handle id " + id +
						". The workspace service currently does not have a " +
						"connection to the handle service and so cannot " +
						"process objects containing handle IDs.",
						TYPE, associatedObject, "" + id,
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
			if (client == null) {
				throw new IdReferenceHandlerException(
						"The workspace is not currently connected to the Handle Service and cannot process Handle ids.",
						TYPE, null);
			}
			final Long allreadable;
			try {
				final AbstractHandleClient ahc = new AbstractHandleClient(
						client.getURL(), userToken);
				if (client.getURL().getProtocol().equals("http")) {
					ahc.setIsInsecureHttpConnectionAllowed(true);
				}
				allreadable = ahc.isOwner(new LinkedList<String>(handles));
			} catch (UnauthorizedException e) {
				throw new IdReferenceHandlerException(
						"Authorization for Handle Service failed. The server said: "
								+ e.getLocalizedMessage(), TYPE, e);
			} catch (IOException e) {
				throw new IdReferenceHandlerException(
						"There was a communication error while trying to contact the Handle Service: "
						+ e.getLocalizedMessage(), TYPE, e);
			} catch (JsonClientException e) {
				throw new IdReferenceHandlerException(
						"There was an unexpected error while trying to contact the Handle Service: "
						+ e.getLocalizedMessage(), TYPE, e);
			}
			//per Tom Brettin, 0 = false, anything else = true
			if (allreadable == 0) {
				throw new IdReferenceHandlerException(
						"The Handle Service reported that at least one of " +
						"the handles contained in the objects in this call " +
						"is not accessible - it may not exist, or the " +
						"supplied credentials may not own the node, or some " +
						"other reason. The call cannot complete.", TYPE, null);
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
			return TYPE;
		}
	}
}
