package us.kbase.workspace.kbase;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.auth.AuthToken;
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
	//TODO 2 tests for handler id extraction, verification, etc.
/*
	crusherofheads@icrushdeheads:~$ export PERL5LIB=/kb/deployment/lib
	crusherofheads@icrushdeheads:~$ export KB_DEPLOYMENT_CONFIG=/kb/deployment/deployment.cfg
	crusherofheads@icrushdeheads:~$ plackup /kb/deployment/lib/AbstractHandle.psgi
	2014/07/27 23:26:42 15811 reading config from /kb/deployment/deployment.cfg
	2014/07/27 23:26:42 15811 using http://localhost:7044 as the default shock server
	{"attribute_indexes":[""],"contact":"shock-admin@kbase.us","documentation":"http://localhost:7044/wiki/","id":"Shock","resources":["node"],"type":"Shock","url":"http://localhost:7044/","version":"0.8.16"}DBI connect('hsi;host=localhost','hsi',...) failed: Access denied for user 'hsi'@'localhost' (using password: YES) at /kb/deployment/lib/Bio/KBase/AbstractHandle/AbstractHandleImpl.pm line 67
	Cannot read config file /etc/log/log.conf at /kb/deployment/lib/Bio/KBase/Log.pm line 282.
	HTTP::Server::PSGI: Accepting connections at http://0:5000/
	
	--port for port
*/
	
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
	
	public class HandleIdHandler<T> implements IdReferenceHandler<T> {
		// seems like this might be a candidate for an abstract class, lock/processed/null checking common code

		private final Map<T, Set<String>> ids = new HashMap<T, Set<String>>();
		private boolean processed = false;
		private boolean locked = false;
		
		private HandleIdHandler() {}
		
		@Override
		public boolean addId(T associatedObject, String id,
				List<String> attributes) throws IdReferenceHandlerException,
				HandlerLockedException {
			if (locked) {
				throw new HandlerLockedException("This handler is locked");
			}
			if (associatedObject == null) {
				throw new NullPointerException(
						"associatedObject cannot be null");
			}
			if (handleService == null) {
				throw new IdReferenceException("Found handle id " + id +
						". The workspace service currently does not have a " +
						"connection to the handle service and so cannot " +
						"process objects containing handle IDs.",
						type, associatedObject, id,
						attributes, null);
			}
			try {
				if (Integer.parseInt(id) < 0) {
					throw new IdReferenceException("Illegal handle id " + id +
							", must be positive", type, associatedObject, id,
							attributes, null);
				}
			} catch (NumberFormatException nfe) {
				throw new IdParseException("Illegal handle id " + id +
						", expected an integer ", type, associatedObject,
						id, attributes, null);
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
		public void processIds() throws IdReferenceHandlerException {
			processed = true;
			locked = true;
			// TODO 1 needs handle service to process IDs
			// 1) collect all IDs
			// 2) call handle service
			// 4) throw error if not all readable by user
			
		}

		@Override
		public RemappedId getRemappedId(String oldId) throws NoSuchIdException {
			if (!processed) {
				throw new IllegalStateException(
						"IDs haven't been processed yet");
			}
			for (final T assobj: ids.keySet()) {
				if (ids.get(assobj).contains(oldId)) {
					return new SimpleRemappedId(oldId);
				}
			}
			throw new NoSuchIdException("No such ID contained in this mapper: "
					+ oldId);
		}

		@Override
		public Set<RemappedId> getRemappedIds(T associatedObject) {
			if (!processed) {
				throw new IllegalStateException(
						"IDs haven't been processed yet");
			}
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
		public void lock() {
			locked = true;
		}

		@Override
		public IdReferenceType getIdType() {
			return type;
		}
	}
}
