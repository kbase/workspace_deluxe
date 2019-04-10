package us.kbase.typedobj.idref;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkNoNullsOrEmpties;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A set of {@link IdReferencePermissionHandler}s.
 * Created by {@link IdReferenceHandlerSetFactoryBuilder#createPermissionHandler()} or
 * {@link IdReferenceHandlerSetFactoryBuilder#createPermissionHandler(String)}.
 * @author gaprice@lbl.gov
 *
 */
public class IdReferencePermissionHandlerSet {
	
	private final Map<IdReferenceType, IdReferencePermissionHandler> handlers;
	
	/** A handler that modifies permissions on a set of IDs.
	 * @author gaprice@lbl.gov
	 *
	 */
	public interface IdReferencePermissionHandler {
		
		/** Add read permission to the data associated with the given IDs.
		 * Null or empty collections are ignored.
		 * @param ids the IDs to modify.
		 * @throws IdReferencePermissionHandlerException if adding read permissions fails.
		 */
		public void addReadPermission(final Collection<String> ids)
				throws IdReferencePermissionHandlerException;
		
	}

	/** Create the permission handler set.
	 * @param handlers the handlers.
	 */
	protected IdReferencePermissionHandlerSet(
			final Map<IdReferenceType, IdReferencePermissionHandler> handlers) {
		this.handlers = new HashMap<IdReferenceType, IdReferencePermissionHandler>(
				handlers);
	}

	/** Returns true if this handler set contains a handler for the ID type
	 * specified.
	 * @param idType the type of ID to check.
	 * @return true if this handler set contains a handler for the ID type
	 * specified.
	 */
	public boolean hasHandler(final IdReferenceType idType) {
		return handlers.containsKey(requireNonNull(idType, "idType"));
	}
	
	
	/** Get the id types with registered handlers.
	 * @return the id types with handlers that have been added to this handler
	 * set.
	 */
	public Set<IdReferenceType> getIDTypes() {
		return handlers.keySet();
	}
	
	/** Add read permission to data associated with a set of IDs.
	 * @param idType the type of the IDs. This determines which
	 * {@link IdReferencePermissionHandler} will handle the request.
	 * @param ids the IDs to modify.
	 * @throws IdReferencePermissionHandlerException if an error occurs setting the permissions.
	 */
	public void addReadPermission(final IdReferenceType idType, final Collection<String> ids)
			throws IdReferencePermissionHandlerException {
		if (!hasHandler(idType)) {
			throw new NoSuchIdReferenceHandlerException(
					"There is no handler registered for the ID type " + idType.getType());
		}
		checkNoNullsOrEmpties(ids, "ids");
		// may want to catch and rethrow a multi-exception at the end. YAGNI for now.
		handlers.get(idType).addReadPermission(ids);
	}
	
	/** A general permission handler exception.
	 * @author gaprice@lbl.gov
	 *
	 */
	@SuppressWarnings("serial")
	public static class IdReferencePermissionHandlerException extends Exception {

		public IdReferencePermissionHandlerException(final String message) {
			super(message);
		}
		
		public IdReferencePermissionHandlerException(final String message, final Throwable cause) {
			super(message, cause);
		}
		
	}
	
}
