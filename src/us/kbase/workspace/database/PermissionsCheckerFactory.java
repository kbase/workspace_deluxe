package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;
import static us.kbase.workspace.database.Util.noNulls;
import static us.kbase.common.utils.StringUtils.checkString;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

/** Provides permission checking for workspaces and objects.
 * 
 * Not thread safe - create a new factory per thread.
 * @author gaprice@lbl.gov
 *
 */
public class PermissionsCheckerFactory {
	
	private static final Map<Permission, String> ops = ImmutableMap.of(
			Permission.NONE, "no permission required, this should never actually show up anywhere",
			Permission.READ, "read",
			Permission.WRITE, "write to",
			Permission.ADMIN, "administrate",
			Permission.OWNER, "administrate as an owner");
	
	private final WorkspaceDatabase db;
	private final WorkspaceUser user;
	
	/** Create a new factory.
	 * @param db the workspace database that will be queried for permissions.
	 * @param user the user for whom permissions will be checked, or null for an anonymous user.
	 */
	public PermissionsCheckerFactory(final WorkspaceDatabase db, final WorkspaceUser user) {
		nonNull(db, "db");
		this.db = db;
		this.user = user; //TODO CODE make an AnonymousUser class or something, or use Optional
	}
	
	/** Returns the user associated with this permissions checker factory, or null for an anonymous
	 * user.
	 * @return the user.
	 */
	public WorkspaceUser getUser() {
		return user;
	}
	
	private abstract class AbstractPermissionsChecker<T> {
		final Permission perm;
		String operation;
		
		private AbstractPermissionsChecker(final Permission perm) {
			nonNull(perm, "perm");
			this.perm = perm;
			this.operation = ops.get(perm);
		}
		
		/** Set the operation name for use when throwing an error when when a required
		 * permission is not met. By default the operation names by permission are:
		 * <ul>
		 * <li>NONE: no permission required, this should never actually show up anywhere</li>
		 * <li>READ: read</li>
		 * <li>WRITE: write to</li>
		 * <li>ADMIN: administrate</li>
		 * </ul>
		 * 
		 * @param operation the operation name.
		 * @return this checker.
		 */
		public T withOperation(final String operation) {
			checkString(operation, "operation");
			this.operation = operation;
			return getThis();
		}
		
		abstract T getThis();
	}
	
	/** Get a permissions checker for multiple workspaces.
	 * @param workspaces the workspaces for which permissions will be checked.
	 * @param perm the required permission.
	 * @return a new permissions checker.
	 */
	public WorkspacePermissionsChecker getWorkspaceChecker(
			final List<WorkspaceIdentifier> workspaces,
			final Permission perm) {
		return new WorkspacePermissionsChecker(workspaces, perm);
	}
	
	/** A permissions checker for multiple workspaces.
	 * @author gaprice@lbl.gov
	 *
	 */
	public class WorkspacePermissionsChecker
			extends AbstractPermissionsChecker<WorkspacePermissionsChecker> {
		
		private final List<WorkspaceIdentifier> workspaces;
		
		private WorkspacePermissionsChecker(
				final List<WorkspaceIdentifier> workspaces,
				final Permission perm) {
			super(perm);
			nonNull(workspaces, "workspaces");
			if (workspaces.isEmpty()) {
				throw new IllegalArgumentException("No workspace identifiers provided");
			}
			noNulls(workspaces, "null object in workspaces");
			
			this.workspaces = new LinkedList<>(workspaces);
		}
		
		@Override
		WorkspacePermissionsChecker getThis() {
			return this;
		}
		
		/** Check the permissions on the selected workspaces for the user and get the resolved
		 * workspaces if permissions requirements were met.
		 * @return the original workspaces mapped to resolved workspaces.
		 * @throws NoSuchWorkspaceException if one of the workspaces is missing or
		 * deleted.
		 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
		 * the storage system.
		 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
		 * @throws WorkspaceAuthorizationException if the user is not authorized to access one of
		 * the workspaces.
		 */
		public Map<WorkspaceIdentifier, ResolvedWorkspaceID> check()
				throws NoSuchWorkspaceException, WorkspaceCommunicationException,
					CorruptWorkspaceDBException, WorkspaceAuthorizationException {
			final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
					db.resolveWorkspaces(new HashSet<>(workspaces));
			final PermissionSet perms = db.getPermissions(user, new HashSet<>(rwsis.values()));
			for (final Entry<WorkspaceIdentifier, ResolvedWorkspaceID> e: rwsis.entrySet()) {
				comparePermission(user, perm, perms.getPermission(e.getValue()),
						e.getKey(), operation);
				checkLocked(perm, e.getValue());
			}
			return rwsis;
		}
	}
	
	/** Get a permissions checker for a single workspace.
	 * @param workspace the workspace for which permissions will be checked.
	 * @param perm the required permission.
	 * @return a new permissions checker.
	 */
	public SingleWorkspacePermissionsChecker getWorkspaceChecker(
			final WorkspaceIdentifier workspace,
			final Permission perm) {
		return new SingleWorkspacePermissionsChecker(workspace, perm);
	}
	
	/** A permissions checker for a single workspace.
	 * @author gaprice@lbl.gov
	 *
	 */
	public class SingleWorkspacePermissionsChecker extends
			AbstractPermissionsChecker<SingleWorkspacePermissionsChecker> {
		
		final WorkspacePermissionsChecker checker;
		final WorkspaceIdentifier wsi;
		
		private SingleWorkspacePermissionsChecker(
				final WorkspaceIdentifier workspace,
				final Permission perm) {
			super(perm);
			nonNull(workspace, "Workspace identifier cannot be null");
			checker = getWorkspaceChecker(Arrays.asList(workspace), perm);
			wsi = workspace;
		}
		
		@Override
		SingleWorkspacePermissionsChecker getThis() {
			return this;
		}
		
		@Override
		public SingleWorkspacePermissionsChecker withOperation(final String operation) {
			checker.withOperation(operation);
			return getThis();
		}
		
		/** Check the permissions on the selected workspace for the user and get the resolved
		 * workspace if permissions requirements were met.
		 * @return the resolved workspace.
		 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is
		 * deleted.
		 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
		 * the storage system.
		 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
		 * @throws WorkspaceAuthorizationException if the user is not authorized to access the
		 * workspace.
		 */
		public ResolvedWorkspaceID check()
				throws NoSuchWorkspaceException, WorkspaceCommunicationException,
					CorruptWorkspaceDBException, WorkspaceAuthorizationException {
			return checker.check().get(wsi);
		}
	}
	
	/** Get a permissions checker for multiple objects.
	 * @param objects the objects for which permissions will be checked.
	 * @param perm the required permission.
	 * @return a new permissions checker.
	 */
	public ObjectPermissionsChecker getObjectChecker(
			final Collection<ObjectIdentifier> objects,
			final Permission perm) {
		return new ObjectPermissionsChecker(objects, perm);
	}
	
	private abstract class AbstractObjectPermissionsChecker<T> extends
			AbstractPermissionsChecker<T> {
		
		boolean includeDeletedWorkspaces = false;
		boolean suppressErrors = false;
		
		public AbstractObjectPermissionsChecker(final Permission perm) {
			super(perm);
		}
		
		/** Include deleted workspaces in the output of the check() method if found. Use this
		 * option with caution. All errors are suppressed and missing or inaccessible workspaces
		 * will not cause an exception to be thrown - they will be missing in the output.
		 * 
		 * @return this checker.
		 */
		public T withIncludeDeletedWorkspaces() {
			this.suppressErrors = true;
			this.includeDeletedWorkspaces = true;
			return getThis();
		}
		
		/** Rather than throwing an exception, do not include missing and inaccessible workspaces
		 * in the output from the check() method. Deleted workspaces are not included in the
		 * output.
		 * @param suppressErrors true to ignore missing and inaccessible workspaces
		 * in the output, false (the default) to throw an exception if found.
		 * @return this checker.
		 */
		public T withSuppressErrors(final boolean suppressErrors) {
			this.suppressErrors = suppressErrors;
			this.includeDeletedWorkspaces = false;
			return getThis();
		}
	}
	
	/** A permissions checker for multiple objects.
	 * @author gaprice@lbl.gov
	 *
	 */
	public class ObjectPermissionsChecker extends
			AbstractObjectPermissionsChecker<ObjectPermissionsChecker> {
		
		private final Collection<ObjectIdentifier> objects;
		
		private ObjectPermissionsChecker(
				final Collection<ObjectIdentifier> objects,
				final Permission perm) {
			super(perm);
			nonNull(objects, "objects");
			if (objects.isEmpty()) {
				throw new IllegalArgumentException("No object identifiers provided");
			}
			noNulls(objects, "null object in objects");
			
			this.objects = new LinkedList<>(objects);
		}
		
		@Override
		ObjectPermissionsChecker getThis() {
			return this;
		}
		
		/** Check the permissions on the selected objects for the user and get the resolved
		 * objects if permissions requirements were met.
		 * @return the original objects mapped to resolved objects.
		 * @throws InaccessibleObjectException if an object is inaccessible.
		 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
		 * the storage system.
		 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
		 */
		public Map<ObjectIdentifier, ObjectIDResolvedWS> check()
				throws WorkspaceCommunicationException, InaccessibleObjectException,
					CorruptWorkspaceDBException {
			
			//map is for error purposes only - only stores the most recent object
			//associated with a workspace
			final Map<WorkspaceIdentifier, ObjectIdentifier> wsis = new HashMap<>();
			for (final ObjectIdentifier o: objects) {
				wsis.put(o.getWorkspaceIdentifier(), o);
			}
			final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis;
			try {
				rwsis = db.resolveWorkspaces(wsis.keySet(), suppressErrors);
			} catch (NoSuchWorkspaceException nswe) {
				final ObjectIdentifier obj = wsis.get(nswe.getMissingWorkspace());
				throw new InaccessibleObjectException(String.format(
						"Object %s cannot be accessed: %s",
						obj.getIdentifierString(), nswe.getLocalizedMessage()), obj, nswe);
			}
			if (suppressErrors && !includeDeletedWorkspaces) {
				removeDeletedWorkspaces(rwsis);
			}
			final PermissionSet perms = db.getPermissions(user, new HashSet<>(rwsis.values()));
			final Map<ObjectIdentifier, ObjectIDResolvedWS> ret = new HashMap<>();
			for (final ObjectIdentifier o: objects) {
				if (!rwsis.containsKey(o.getWorkspaceIdentifier())) {
					continue; //missing workspace
				}
				final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
				try {
					comparePermission(user, perm, perms.getPermission(r), o, operation);
				} catch (WorkspaceAuthorizationException wae) {
					if (suppressErrors) {
						continue;
					} else {
						// contrary to ECLEmma's output, this path is in fact tested
						throwInaccessibleObjectException(o, wae);
					}
				}
				try {
					checkLocked(perm, r); // no suppressing errors here.
				} catch (WorkspaceAuthorizationException wae) {
					throwInaccessibleObjectException(o, wae);
				}
				ret.put(o, o.resolveWorkspace(r));
			}
			return ret;
		}
	}
	
	/** Get a permissions checker for a single object.
	 * @param object the object for which permissions will be checked.
	 * @param perm the required permission.
	 * @return a new permissions checker.
	 */
	public SingleObjectPermissionsChecker getObjectChecker(
			final ObjectIdentifier object,
			final Permission perm) {
		return new SingleObjectPermissionsChecker(object, perm);
	}
	
	/** A permissions checker for a single object.
	 * @author gaprice@lbl.gov
	 *
	 */
	public class SingleObjectPermissionsChecker extends
			AbstractObjectPermissionsChecker<SingleObjectPermissionsChecker> {
		
		final ObjectPermissionsChecker checker;
		final ObjectIdentifier object;
		
		private SingleObjectPermissionsChecker(
				final ObjectIdentifier object,
				final Permission perm) {
			super(perm);
			nonNull(object, "Object identifier cannot be null");
			checker = getObjectChecker(Arrays.asList(object), perm);
			this.object = object;
		}
		
		@Override
		SingleObjectPermissionsChecker getThis() {
			return this;
		}
		
		@Override
		public SingleObjectPermissionsChecker withOperation(final String operation) {
			checker.withOperation(operation);
			return getThis();
		}
		
		@Override
		public SingleObjectPermissionsChecker withIncludeDeletedWorkspaces() {
			throw new UnsupportedOperationException("Unsupported for single objects");
		}
		
		@Override
		public SingleObjectPermissionsChecker withSuppressErrors(final boolean suppressErrors) {
			throw new UnsupportedOperationException("Unsupported for single objects");
		}
		
		/** Check the permissions on the selected object for the user and get the resolved
		 * object if permissions requirements were met.
		 * @return the resolved object2.
		 * @throws InaccessibleObjectException if the object is inaccessible.
		 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
		 * the storage system.
		 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
		 */
		public ObjectIDResolvedWS check() throws WorkspaceCommunicationException,
				InaccessibleObjectException, CorruptWorkspaceDBException {
			return checker.check().get(object);
		}
	}
	
	/** Check if a workspace is locked and therefore cannot be altered.
	 * @param perm the permission required for the operation. If the permission is WRITE or greater
	 * and the workspace is locked, an authorization exception will be thrown.
	 * @param rwsi the workspace in question.
	 * @throws WorkspaceAuthorizationException if the workspace is locked and the permission
	 * implies that the workspace will be altered.
	 */
	public static void checkLocked(
			final Permission perm,
			final ResolvedWorkspaceID rwsi)
			throws WorkspaceAuthorizationException {
		if (perm.compareTo(Permission.READ) > 0 && rwsi.isLocked()) {
			throw new WorkspaceAuthorizationException("The workspace with id "
					+ rwsi.getID() + ", name " + rwsi.getName() +
					", is locked and may not be modified");
		}
	}
	
	private void comparePermission(
			final WorkspaceUser user,
			final Permission required,
			final Permission available,
			final ObjectIdentifier oi,
			final String operation)
			throws WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae = comparePermission(user, required, available,
				oi.getWorkspaceIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(oi);
			throw wae;
		}
	}
	
	/** Compare an available permission against a required permission for a user and throw an
	 * error if the available permission is not greater than or equal to the required permission. 
	 * @param user the user with the permission.
	 * @param required the permission required for the operation.
	 * @param available the permission the user possesses.
	 * @param wsi the workspace upon which the operation will take place.
	 * @param operation the name of the operation - typically "read", "write to", etc.
	 * @throws WorkspaceAuthorizationException if the available permission is not greater then or
	 * equal to the available permission.
	 */
	public static void comparePermission(
			final WorkspaceUser user,
			final Permission required,
			final Permission available,
			final WorkspaceIdentifier wsi,
			final String operation)
			throws WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae =
				comparePermission(user, required, available, wsi.getIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(wsi);
			throw wae;
		}
	}
	
	private static WorkspaceAuthorizationException comparePermission(
			final WorkspaceUser user,
			final Permission required,
			final Permission available,
			final String identifier,
			final String operation) {
		if (required.compareTo(available) > 0) {
			final String err = user == null ?
					"Anonymous users may not %s workspace %s" :
					"User " + user.getUser() + " may not %s workspace %s";
			final WorkspaceAuthorizationException wae = 
					new WorkspaceAuthorizationException(String.format(err, operation, identifier));
			return wae;
		}
		return null;
	}
	
	private void removeDeletedWorkspaces(
			final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis) {
		final Iterator<WorkspaceIdentifier> i = rwsis.keySet().iterator();
		while (i.hasNext()) {
			if (rwsis.get(i.next()).isDeleted()) {
				i.remove();
			}
		}
	}

	private void throwInaccessibleObjectException(
			final ObjectIdentifier o,
			final WorkspaceAuthorizationException wae)
			throws InaccessibleObjectException {
		throw new InaccessibleObjectException(String.format("Object %s cannot be accessed: %s",
				o.getIdentifierString(), wae.getLocalizedMessage()), o, wae);
	}
}
