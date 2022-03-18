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
import us.kbase.shock.client.ShockNodeId;
import us.kbase.shock.client.ShockUserId;
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
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandler;
import us.kbase.typedobj.idref.IdReferencePermissionHandlerSet.IdReferencePermissionHandlerException;
import us.kbase.typedobj.idref.RemappedId;

// TODO BYTESTREAM_CLIENT redo the Shock client so it's called a Blobstore client or something.
// Names are important but this is kind of low priority

/** A handler factory for bytestream (aka KBase Blobstore) IDs.
 * 
 * Note that bytestream IDs may not have attributes, and any attributes supplied to
 * {@link us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler#addId(Object, String, List)}
 * will be ignored.
 * 
 * The factory uses a Shock client for communicating with the KBase Blobstore.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class BytestreamIdHandlerFactory implements IdReferenceHandlerFactory {
	
	/** Given a Shock client, provides a new Shock client with no token.
	 * @author gaprice@lbl.gov
	 *
	 */
	public interface BytestreamClientCloner {
		
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
	public static final IdReferenceType TYPE = new IdReferenceType("bytestream");
	private final BasicShockClient adminClient;
	private final BytestreamClientCloner cloner;
	
	/** Create the bytestream ID handler.
	 * @param adminClient a Shock client with a Blobstore administrator token. All nodes passed to
	 * this handler will be owned by the administrator user. Pass null to 
	 * cause an exception to be thrown if a shock id is encountered.
	 * @param cloner a bytestream client cloner.
	 */
	public BytestreamIdHandlerFactory(
			final BasicShockClient adminClient,
			final BytestreamClientCloner cloner) {
		this.adminClient = adminClient;
		this.cloner = adminClient == null ? null : requireNonNull(cloner, "cloner");
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(
			final Class<T> clazz,
			final AuthToken userToken) {
		return new BytestreamIdHandler<T>(requireNonNull(userToken, "userToken"));
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler() {
		return new BytestreamPermissionsHandler(null);
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler(final String userName) {
		return new BytestreamPermissionsHandler(userName);
	}

	@Override
	public IdReferenceType getIDType() {
		return TYPE;
	}
	
	@Override
	public List<DependencyStatus> getDependencyStatus() {
		if (adminClient == null) {
			return Collections.emptyList();
		}
		try {
			return Arrays.asList(new DependencyStatus(true, "OK", "Linked Shock for IDs",
					adminClient.getRemoteVersion()));
		} catch (InvalidShockUrlException | IOException e) {
			return Arrays.asList(new DependencyStatus(
					false, e.getMessage(), "Linked Shock for IDs", "Unknown"));
		}
	}
	
	private class BytestreamPermissionsHandler implements IdReferencePermissionHandler {
		
		private final String user;

		private BytestreamPermissionsHandler(final String userName) {
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
						"There is no connection configured for bytestream storage " +
						"and bytestream IDs cannot be processed.");
			}
			// could check node id format up front, but for the general use case shouldn't happen
			String lastid = null; // yuck. Improve this later.
			try {
				if (user == null) {
					for (final String id: ids) {
						lastid = id;
						adminClient.setPubliclyReadable(getNodeID(id), true);
					}
				} else {
					for (final String id: ids) {
						lastid = id;
						adminClient.addToNodeAcl(
								getNodeID(id), Arrays.asList(user), ShockACLType.READ);
					}
				}
			} catch (IOException e) {
				throw new IdReferencePermissionHandlerException(
						"There was an IO problem while attempting to set bytestream ACLs on node " +
						lastid + ": " + e.getMessage(), e);
			} catch (ShockHttpException e) {
				throw new IdReferencePermissionHandlerException(
						"Bytestream storage reported a problem while attempting to set ACLs on " +
						"node " + lastid + ": " + e.getMessage(), e);
			}
		}

		private ShockNodeId getNodeID(final String id)
				throws IdReferencePermissionHandlerException {
			try {
				return new ShockNodeId(id);
			} catch (IllegalArgumentException e) {
				throw new IdReferencePermissionHandlerException("Illegal bytestream ID: " + id);
			}
		}
	}
	
	private class BytestreamIdHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private final Map<String, String> remapped = new HashMap<>();
		private final AuthToken userToken;
		
		private BytestreamIdHandler(final AuthToken userToken) {
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
				throw new IdReferenceException("Found bytestream id " + id +
						". There is no connection configured for bytestream IDs " +
						"and so objects containing bytestream IDs cannot be processed.",
						TYPE, associatedObject, id, null, null);
			}
			try {
				new ShockNodeId(id);
			} catch (IllegalArgumentException e) {
				throw new IdParseException("Illegal bytestream ID: " + id,
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
			final Set<String> unowned = ensureNodesUserOwnedAndGetUnownedNodes();
			for (final T assObj: ids.keySet()) {
				for (final String node: ids.get(assObj)) {
					if (!remapped.containsKey(node)) {
						remapped.put(node, unowned.contains(node) ? own(node) : node);
					}
				}
			}
		}

		// this method assumes the adminClient really is an admin and the node exists.
		private String own(final String node) throws IdReferenceHandlerException {
			final String adminUser = adminClient.getToken().getUserName();
			try {
				// for errors, could go back and delete the other copies... YAGNI for now.
				final ShockACL acls = adminClient.addToNodeAcl(
						new ShockNodeId(node), Arrays.asList(adminUser), ShockACLType.OWNER);
				removeFromACL(node, adminUser, acls.getWrite(), ShockACLType.WRITE);
				removeFromACL(node, adminUser, acls.getDelete(), ShockACLType.DELETE);
			} catch (IOException e) {
				throw new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact bytestream storage " +
						"to alter nodes: " + e.getMessage(), TYPE, e);
			} catch (ShockHttpException e) {
				throw new IdReferenceHandlerException(
						"Bytestream storage reported a problem while attempting to alter nodes: " +
						e.getMessage(), TYPE, e);
			}
			return node;
		}

		private Set<String> ensureNodesUserOwnedAndGetUnownedNodes()
				throws IdReferenceHandlerException, IdReferenceException {
			final BasicShockClient client;
			try {
				client = cloner.clone(adminClient);
			} catch (IOException | InvalidShockUrlException e) {
				throw new IdReferenceHandlerException(
						"Error contacting bytestream storage to validate IDs: " +
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
						// ensures user can read the node
						acls = client.getACLs(new ShockNodeId(node));
					} catch (ShockAuthorizationException e) {
						throw new IdReferenceException(String.format(
								"User %s cannot read bytestream node %s",
								userToken.getUserName(), node),
								TYPE, assObj, node, null, null);
					} catch (ShockNoNodeException e) {
						throw new IdReferenceException(
								String.format("Bytestream node %s does not exist", node),
								TYPE, assObj, node, null, null);
					} catch (IOException e) {
						throw new IdReferenceHandlerException(
								"There was an IO problem while attempting to contact " +
								"bytestream storage to process IDs: " + e.getMessage(), TYPE, e);
					} catch (ShockHttpException e) {
						throw new IdReferenceHandlerException(
								"Bytestream storage reported a problem while attempting to " +
								"process IDs: " + e.getMessage(), TYPE, e);
					}
					if (acls.getOwner().getUsername().equals(adminUser)) {
						// clean acls up since a user could create a node and then chown it to
						// the workspace
						// TODO BYTESTREAM there's actually no way to unshare nodes when the WS owns them. Need to add a function to do that.
						removeFromACL(node, adminUser, acls.getWrite(), ShockACLType.WRITE);
						removeFromACL(node, adminUser, acls.getDelete(), ShockACLType.DELETE);
					} else if (!acls.getOwner().getUsername().equals(userToken.getUserName())) {
						throw new IdReferenceException(String.format(
								"User %s does not own bytestream node %s",
								userToken.getUserName(), node),
								TYPE, assObj, node, null, null);
					} else {
						unowned.add(node); // own the node
					}
				}
			}
			return unowned;
		}

		// this method assumes the adminClient really is an admin and the node exists.
		private void removeFromACL(
				final String node,
				final String adminUser,
				final List<ShockUserId> users,
				final ShockACLType aclType) throws IdReferenceHandlerException {
			final List<String> strUsers = users.stream().map(u -> u.getUsername())
					.collect(Collectors.toList());
			strUsers.remove(adminUser);
			if (strUsers.isEmpty()) {
				return;
			}
			try {
				adminClient.removeFromNodeAcl(new ShockNodeId(node), strUsers, aclType);
			} catch (IOException e) {
				throw new IdReferenceHandlerException(
						"There was an IO problem while attempting to contact bytestream storage " +
						"to process IDs: " + e.getMessage(), TYPE, e);
			} catch (ShockHttpException e) {
				throw new IdReferenceHandlerException(
						"Bytestream storage reported a problem while attempting to process IDs: " +
						e.getMessage(), TYPE, e);
			}
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
