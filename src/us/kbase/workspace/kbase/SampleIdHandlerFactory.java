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
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.sampleservice.GetSampleACLsParams;
import us.kbase.sampleservice.SampleACLs;
import us.kbase.sampleservice.UpdateSampleACLsParams;
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
 * A factory for building a handler for Sample Service IDs. These are embedded in Workspace
 * Service objects and denoted in the object type specification with an @id sample annotation.
 */
public class SampleIdHandlerFactory implements IdReferenceHandlerFactory {

	public static final IdReferenceType TYPE = new IdReferenceType("sample");
	private final SampleServiceClientWrapper client;
	
	/** Create the Sample ID handler factory.
	 * @param client a sample service client with service administrator write permissions.
	 * Pass null if there is no sample service available - in this
	 * case an error will be thrown if a sample ID is encountered.
	 */
	public SampleIdHandlerFactory(final SampleServiceClientWrapper client) {
		this.client = client;
	}
	
	@Override
	public <T> IdReferenceHandler<T> createHandler(
			final Class<T> clazz,
			final AuthToken userToken) {
		return new SampleIdHandler<T>(requireNonNull(userToken, "userToken"));
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler() {
		return new SamplePermissionsHandler(null);
	}

	@Override
	public IdReferencePermissionHandler createPermissionHandler(final String userName) {
		return new SamplePermissionsHandler(userName);
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
			return Arrays.asList(new DependencyStatus(true, "OK", "Sample service", ver));
		} catch (IOException | JsonClientException e) {
			return Arrays.asList(
					new DependencyStatus(false, e.getMessage(), "Sample service", "Unknown"));
		}
	}
	
	private class SamplePermissionsHandler implements IdReferencePermissionHandler {
		
		private final String user;

		private SamplePermissionsHandler(final String userName) {
			this.user = userName;
		}

		@Override
		public void addReadPermission(final Collection<String> ids)
				throws IdReferencePermissionHandlerException {
			if (ids == null || ids.isEmpty()) {
				return;
			}
			// We assume that the calling framework does not pass null or empty ids.
			// If it does, the sample service will throw an error.
			if (client == null) {
				throw new IdReferencePermissionHandlerException(
						"The workspace is not currently connected to the Sample Service " +
						"and cannot process Sample IDs.");
			}
			// Theoretically the sample service could allow bulk ACL updates. However,
			// this would be the illusion of atomicity since under the hood the Sample Service
			// uses ArangoDB and ArangoDB atomicity level is per document
			// Unless you use a transactions, but transactions aren't atomic in a cluster
			// either as of 3.5
			for (final String id: ids) {
				try {
					if (user == null) {
						client.updateSampleAcls(new UpdateSampleACLsParams().withId(id)
								.withPublicRead(1L).withAsAdmin(1L));
					} else {
						client.updateSampleAcls(new UpdateSampleACLsParams().withId(id)
								.withRead(Arrays.asList(user)).withAtLeast(1L).withAsAdmin(1L));
					}
				} catch (IOException e) {
					throw new IdReferencePermissionHandlerException(
							"There was an IO problem while attempting to set " +
							"Sample ACLs: " + e.getMessage(), e);
				} catch (UnauthorizedException e) {
					throw new IdReferencePermissionHandlerException(
							"Unable to contact the Sample Service - " +
							"the Workspace credentials were rejected: " +
							e.getMessage(), e);
				} catch (ServerException e) {
					// we rely on adequate messaging from the sample service here as to
					// the cause of the problem. May need to change
					throw new IdReferencePermissionHandlerException(
							"The Sample Service reported a problem while attempting " +
									"to set Sample ACLs: " + e.getMessage(), e);
				} catch (JsonClientException e) {
					throw new IdReferencePermissionHandlerException(
							"There was an unexpected problem while contacting the " +
							"Sample Service to set Sample ACLs: " +
							e.getMessage(), e);
				}
			}
		}
	}
	
	private class SampleIdHandler<T> extends IdReferenceHandler<T> {

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private final AuthToken userToken;
		
		private SampleIdHandler(final AuthToken userToken) {
			this.userToken = userToken;
		}
		
		@Override
		protected boolean addIdImpl(
				final T associatedObject,
				final String id,
				final List<String> attributes)
				throws IdReferenceHandlerException {
			if (client == null) {
				throw new IdReferenceException("Found sample id " + id +
						". The workspace service currently does not have a " +
						"connection to the sample service and so cannot " +
						"process objects containing sample IDs.",
						TYPE, associatedObject, id, attributes, null);
			}
			// we assume the calling framework does not pass null or empty ids.
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
			if (ids.isEmpty()) {
				return;
			}
			// cannot get to this point with the client being null
			// tried to stream this but took way too much time to figure out the incantation
			final Map<String, T> idToObj = new HashMap<>();
			for (final T assobj: ids.keySet()) {
				for (final String id: ids.get(assobj)) {
					idToObj.put(id, assobj);
				}
			}
			/* There's a few options here:
			 * 1) Trust that the passed in user token has the correct user name associated with
			 *    the token. Note that this code is intended to be part of a framework that
			 *    validates that.
			 * 2) Make an auth call to validate the token and retrieve the user name. This means
			 *    we don't trust the framework, which has already validated the token, and means
			 *    a duplicate auth call.
			 * 3) Rather than using service admin permissions to get the ACLs, use the token
			 *    as is to get the permissions. That means there will be two representations
			 *    of permission errors the user has to deal with - one if they don't have read
			 *    permission and the sample service throws an error, and one if they don't
			 *    have admin permission (see below). Furthermore, we will have to create a new
			 *    client with the user's token, which means that this code will no longer
			 *    be unit testable, as the client will expect to talk to a service. An option is
			 *    to pass in a function that creates the client that can be itself mocked, but
			 *    that seems a bit ridiculous.
			 * Decision: 1)
			 */
			final String user = userToken.getUserName();
			for (final String id: idToObj.keySet()) {
				final SampleACLs acls;
				try {
					// having a bulk method for getting ACLs would definitely be nice here
					acls = client.getSampleAcls(new GetSampleACLsParams().withId(id)
							.withAsAdmin(1L));
				} catch (UnauthorizedException e) {
					throw new IdReferenceHandlerException(
							"Unable to contact the Sample Service - " +
							"the Workspace credentials were rejected: " +
							e.getLocalizedMessage(), TYPE, e);
				} catch (IOException e) {
					throw new IdReferenceHandlerException(
							"There was a communication error while trying to contact the " +
							"Sample Service: " + e.getLocalizedMessage(), TYPE, e);
				} catch (ServerException e) {
					throw new IdReferenceException(
							"The Sample Service reported a problem while attempting " +
							"to get Sample ACLs: " + e.getMessage(),
							TYPE, idToObj.get(id), id, null, e);
				} catch (JsonClientException e) {
					throw new IdReferenceHandlerException(
							"There was an unexpected error while trying to contact the " +
							"Sample Service: " + e.getLocalizedMessage(), TYPE, e);
				}
				if (user != acls.getOwner() && !acls.getAdmin().contains(user)) {
					throw new IdReferenceException(
							String.format("User %s does not have administrative permissions " +
									"for sample %s", user, id),
							TYPE, idToObj.get(id), id, null, null);
				}
			}
		}

		@Override
		protected RemappedId getRemappedIdImpl(final String oldId)
				throws NoSuchIdException {
			for (final T assobj: ids.keySet()) {
				if (ids.get(assobj).contains(oldId)) {
					return new SimpleRemappedId(oldId);
				}
			}
			throw new NoSuchIdException("No such ID contained in this mapper: " + oldId);
		}

		@Override
		protected Set<RemappedId> getRemappedIdsImpl(final T associatedObject) {
			return ids.getOrDefault(associatedObject, Collections.emptySet())
					.stream().map(i -> new SimpleRemappedId(i)).collect(Collectors.toSet());
		}

		@Override
		public IdReferenceType getIdType() {
			return TYPE;
		}
	}
}
