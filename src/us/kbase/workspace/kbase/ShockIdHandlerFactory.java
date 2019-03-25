package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockACL;
import us.kbase.shock.client.ShockACLType;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.exceptions.InvalidShockUrlException;
import us.kbase.shock.client.exceptions.ShockAuthorizationException;
import us.kbase.shock.client.exceptions.ShockHttpException;
import us.kbase.shock.client.exceptions.ShockNoNodeException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
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

/** A handler factory for shock IDs. Note that shock IDs may not have attributes, and any
 * attributes supplied to
 * {@link us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler#addId(Object, String, List)}
 * will be ignored.
 * @author gaprice@lbl.gov
 *
 */
public class ShockIdHandlerFactory implements IdReferenceHandlerFactory {

	//TODO SHOCKID new params - shock url, shock user, shock token (toggle required based on shock url)
	//TODO SHOCKID integrate into startup
	//TODO SHOCKID integration tests
	//TODO SHOCKID documentation, compare vs. handle ID (shock int docs, type @id docs)

	/** Given a Shock client, provides a new Shock client with no token.
	 * @author gaprice@lbl.gov
	 *
	 */
	public interface ShockClientCloner {
		
		/** Clone the given client without a token.
		 * @param source the source client.
		 * @return the new client.
		 * @throws InvalidShockUrlException if the shock URL is invalid.
		 * @throws IOException if an IO error occurs contacting the shock service.
		 */
		BasicShockClient clone(BasicShockClient source)
				throws IOException, InvalidShockUrlException;
	}
	
	/** The type of ID this ID handler processes. */
	public static final IdReferenceType TYPE = new IdReferenceType("shock");
	private final BasicShockClient adminClient;
	private final ShockClientCloner cloner;
	
	/** Create the shock ID handler.
	 * @param adminClient a Shock client with a Shock administrator token. Pass null to 
	 * cause an exception to be thrown if a shock id is encountered.
	 * @param cloner a Shock client cloner.
	 */
	public ShockIdHandlerFactory(
			final BasicShockClient adminClient,
			final ShockClientCloner cloner) {
		this.adminClient = adminClient;
		this.cloner = adminClient == null ? null : requireNonNull(cloner, "cloner");
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(
			final Class<T> clazz,
			final AuthToken userToken) {
		return new ShockIdHandler<T>(requireNonNull(userToken, "userToken"));
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler() {
		return new ShockPermissionsHandler(null);
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler(final String userName) {
		return new ShockPermissionsHandler(userName);
	}

	@Override
	public IdReferenceType getIDType() {
		return TYPE;
	}
	
	private class ShockPermissionsHandler implements IdReferencePermissionHandler {
		
		private final String user;

		private ShockPermissionsHandler(final String userName) {
			this.user = userName;
		}

		@Override
		public void addReadPermission(final Collection<String> ids)
				throws IdReferencePermissionHandlerException {
			if (ids == null || ids.isEmpty()) {
				return;
			}
			if (adminClient == null) {
				throw new IdReferencePermissionHandlerException(
						"There is no connection configured for the Shock Service " +
						"and Shock IDs cannot be processed.");
			}
			// could check node id format up front, but for the general use case shouldn't happen
			try {
				if (user == null) {
					for (final String id: ids) {
						adminClient.setPubliclyReadable(getNodeID(id), true);
					}
				} else {
					for (final String id: ids) {
						adminClient.addToNodeAcl(
								getNodeID(id), Arrays.asList(user), ShockACLType.READ);
					}
				}
			} catch (IOException e) {
				throw new IdReferencePermissionHandlerException(
						"There was an IO problem while attempting to set Shock ACLs: " +
						e.getMessage(), e);
			} catch (ShockHttpException e) {
				throw new IdReferencePermissionHandlerException(
						"Shock reported a problem while attempting to set Shock ACLs: " +
						e.getMessage(), e);
			}
		}

		private ShockNodeId getNodeID(final String id)
				throws IdReferencePermissionHandlerException {
			try {
				return new ShockNodeId(id);
			} catch (IllegalArgumentException e) {
				throw new IdReferencePermissionHandlerException("Illegal shock ID: " + id);
			}
		}
	}
	
	private class ShockIdHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private final Map<String, String> remapped = new HashMap<>();
		private final AuthToken userToken;
		
		private ShockIdHandler(final AuthToken userToken) {
			this.userToken = requireNonNull(userToken, "userToken");
		}
		
		@Override
		protected boolean addIdImpl(
				final T associatedObject,
				final String id,
				final List<String> attributes)
				throws IdReferenceException {
			// nulls are checked by the superclass
			if (adminClient == null) {
				throw new IdReferenceException("Found shock id " + id +
						". There is no connection configured for the Shock Service " +
						"and so objects containing shock IDs cannot be processed.",
						TYPE, associatedObject, id, null, null);
			}
			try {
				new ShockNodeId(id);
			} catch (IllegalArgumentException e) {
				throw new IdParseException("Illegal shock ID: " + id,
						TYPE, associatedObject, id, null, null);
			}
			boolean unique = true;
			if (!ids.containsKey(associatedObject)) {
				ids.put(associatedObject, new HashSet<>());
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
			if (ids.isEmpty()) {
				return;
			}
			// check readability first, then make copies
			final Set<String> unowned = ensureNodesUserReadableAndGetUnownedNodes();
			for (final T assObj: ids.keySet()) {
				for (final String node: ids.get(assObj)) {
					if (!remapped.containsKey(node)) {
						remapped.put(node, unowned.contains(node) ? copy(node) : node);
					}
				}
			}
		}

		private String copy(final String node) throws IdReferenceHandlerException {
			final ShockNode newnode;
			try {
				newnode = adminClient.copyNode(new ShockNodeId(node), true);
				// for errors, could go back and delete the other copies... YAGNI for now.
			} catch (IOException e) {
				throw new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact Shock " +
						"to copy nodes: " + e.getMessage(), TYPE, e);
			} catch (ShockHttpException e) {
				throw new IdReferenceHandlerException(
						"Shock reported a problem while attempting to copy nodes: " +
						e.getMessage(), TYPE, e);
			}
			return newnode.getId().getId();
		}

		private Set<String> ensureNodesUserReadableAndGetUnownedNodes()
				throws IdReferenceHandlerException, IdReferenceException {
			final BasicShockClient client;
			try {
				client = cloner.clone(adminClient);
			} catch (IOException | InvalidShockUrlException e) {
				throw new IdReferenceHandlerException("Error contacting Shock to validate IDs: " +
						e.getMessage(), TYPE, e);
			}
			// prevents client from creating & deleting a shock node every startup
			client.updateToken(userToken);
			final Set<String> seen = new HashSet<>();
			final Set<String> unowned = new HashSet<>();
			final String adminUser = adminClient.getToken().getUserName();
			for (final T assObj: ids.keySet()) {
				for (final String node: ids.get(assObj)) {
					if (seen.contains(node)) {
						continue;
					}
					seen.add(node);
					final ShockACL acls;
					try {
						// checked id syntax on add
						acls = client.getACLs(new ShockNodeId(node));
					} catch (ShockAuthorizationException e) {
						throw new IdReferenceException(String.format(
								"User %s cannot read Shock node %s",
								userToken.getUserName(), node),
								TYPE, assObj, node, null, null);
					} catch (ShockNoNodeException e) {
						throw new IdReferenceException(
								String.format("Shock node %s does not exist", node),
								TYPE, assObj, node, null, null);
					} catch (IOException e) {
						throw new IdReferenceHandlerException(
								"There was an IO problem while attempting to contact Shock " +
								"to process IDs: " + e.getMessage(), TYPE, e);
					} catch (ShockHttpException e) {
						throw new IdReferenceHandlerException(
								"Shock reported a problem while attempting to process IDs: " +
								e.getMessage(), TYPE, e);
					}
					if (!acls.getOwner().getUsername().equals(adminUser)) {
						unowned.add(node);
					}
				}
			}
			return unowned;
		}

		@Override
		protected RemappedId getRemappedIdImpl(final String oldId)
				throws NoSuchIdException {
			if (!remapped.containsKey(oldId)) {
				throw new NoSuchIdException(
						"No such ID contained in this mapper: " + oldId);
			}
			return new SimpleRemappedId(remapped.get(oldId));
		}

		@Override
		protected Set<RemappedId> getRemappedIdsImpl(final T associatedObject) {
			if (!ids.containsKey(associatedObject)) {
				return Collections.emptySet();
			}
			return ids.get(associatedObject).stream()
					.map(i -> new SimpleRemappedId(remapped.get(i))).collect(Collectors.toSet());
		}

		@Override
		public IdReferenceType getIdType() {
			return TYPE;
		}
	}
}
