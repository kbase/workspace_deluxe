package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

public interface WorkspaceDatabase {
	
	public String getBackendType();
	
	//TODO return workspace info insted of resolved WS ID? Almost the same info. Switch to global read boolean on WS first.
	public ResolvedWorkspaceID resolveWorkspace(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;
	
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			Set<WorkspaceIdentifier> rwsis) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;

	public ResolvedWorkspaceID resolveWorkspace(WorkspaceIdentifier wsi,
			boolean allowDeleted) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException;
	
	public Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			Set<WorkspaceIdentifier> wsis, boolean allowDeleted,
			boolean allowMissing)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	public WorkspaceInformation createWorkspace(WorkspaceUser owner,
			String wsname, boolean globalread, String description,
			WorkspaceUserMetadata meta) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;
	
	/** Sets metadata for a workspace, overwriting existing keys if a
	 * duplicate key is supplied.
	 * 
	 * @param wsid the workspace for which metadata will be altered.
	 * @param meta the metadata to add to the workspace.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 * @throws IllegalArgumentException if no metadata is supplied or the 
	 * updated metadata exceeds the allowed size.
	 */
	public void setWorkspaceMeta(ResolvedWorkspaceID wsid,
			WorkspaceUserMetadata meta)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public void removeWorkspaceMetaKey(ResolvedWorkspaceID wsid, String key)
			throws WorkspaceCommunicationException;
	
	/** Clone a workspace.
	 * @param user the user cloning the workspace
	 * @param wsid the ID of the workspace to be cloned.
	 * @param newname the name for the new workspace.
	 * @param globalread whether the new workspace should be globally readable.
	 * @param description the description of the new workspace.
	 * @param meta the metadata of the new workspace.
	 * @param exclude objects to exlude from the cloned workspace.
	 * @return information about the new workspace
	 * @throws PreExistingWorkspaceException if the workspace name already
	 * exists.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is
	 * corrupt.
	 * @throws NoSuchObjectException if an excluded object doesn't exist.
	 */
	public WorkspaceInformation cloneWorkspace(
			WorkspaceUser user,
			ResolvedWorkspaceID wsid,
			String newname,
			boolean globalread,
			String description,
			WorkspaceUserMetadata meta,
			Set<ObjectIDNoWSNoVer> exclude)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			NoSuchObjectException;
	
	public WorkspaceInformation lockWorkspace(WorkspaceUser user,
			ResolvedWorkspaceID wsid)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public void setPermissions(ResolvedWorkspaceID rwsi,
			List<WorkspaceUser> users, Permission perm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public WorkspaceInformation setWorkspaceOwner(ResolvedWorkspaceID rwsi,
			WorkspaceUser user, WorkspaceUser newUser, String newName)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public void setGlobalPermission(ResolvedWorkspaceID rwsi, Permission perm)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	/** Get the permission for a workspace for one user. Takes global
	 * permissions into account.
	 * 
	 * Will return permissions for deleted workspaces.
	 * 
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspace will be returned. 
	 * @param rwsi the workspace to check.
	 * @return the user's permission for the workspace, taking the workspace's
	 * global permission into account.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	public Permission getPermission(WorkspaceUser user,
			ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	/** Get permissions for a workspace for one user. This method will also
	 *  return whether the workspace is globally readable.
	 * 
	 * Will return permissions for deleted workspaces.
	 *  
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspace will be returned. 
	 * @param rwsi the workspace to check.
	 * @return the user's and global users' permission for the workspace.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	public PermissionSet getPermissions(WorkspaceUser user,
			ResolvedWorkspaceID rwsi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	/** Get permissions for a set of workspaces for one user. If the user is
	 * null, only the global readability of the workspaces will be returned.
	 * 
	 * Will return permissions for deleted workspaces.
	 * 
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspaces will be returned. 
	 * @param rwsis the workspaces to check.
	 * @return the user's and global users' permission for the workspaces.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	public PermissionSet getPermissions(WorkspaceUser user,
			Set<ResolvedWorkspaceID> rwsis)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	/** Returns all the workspaces for which the user has the specified
	 *  permission. If the user is null, only globally readable workspaces will
	 *  be returned if specified.
	 * 
	 * Will return permissions for deleted workspaces.
	 * 
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspaces will be returned. 
	 * @param perm the minimum permission required for a workspace to be
	 * included in the permission set.
	 * @param excludeGlobalRead exclude globally readable workspaces.
	 * @return a set of permissions to workspaces for a user.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	public PermissionSet getPermissions(WorkspaceUser user,
			Permission perm, boolean excludeGlobalRead)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get permissions for a set of workspaces for one user.
	 * 
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspaces will be returned. 
	 * @param rwsis the list of workspaces to check. If empty, all workspaces
	 * to which the user has permission will be returned.
	 * @param perm the minimum permission required for a workspace to be
	 * included in the permission set.
	 * @param excludeGlobalRead exclude globally readable workspaces.
	 * @param excludeDeletedWorkspaces exclude deleted workspaces. Deleted
	 * workspaces in the supplied list are not affected.
	 * @return a set of permissions to workspaces for a user.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	public PermissionSet getPermissions(WorkspaceUser user,
			Set<ResolvedWorkspaceID> rwsis, Permission perm,
			boolean excludeGlobalRead, boolean excludeDeletedWorkspaces)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	/** Returns all users' permissions for a set of workspaces */
	public Map<ResolvedWorkspaceID, Map<User, Permission>> getAllPermissions(
			Set<ResolvedWorkspaceID> rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public WorkspaceInformation getWorkspaceInformation(WorkspaceUser user,
			ResolvedWorkspaceID rwsi) throws CorruptWorkspaceDBException,
			WorkspaceCommunicationException;
	
	public void setWorkspaceDescription(ResolvedWorkspaceID wsid,
			String description) throws WorkspaceCommunicationException;

	public String getWorkspaceDescription(ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public List<ObjectInformation> saveObjects(WorkspaceUser user,
			ResolvedWorkspaceID rwsi, List<ResolvedSaveObject> objects) throws
			NoSuchWorkspaceException, WorkspaceCommunicationException,
			NoSuchObjectException;
	
	/** Get object data and provenance information from the workspace database.
	 * @param objects the objects for which to retrieve data.
	 * @param noData return provenance only if true.
	 * @param exceptIfDeleted throw an exception if deleted.
	 * @param includeDeleted include information from deleted objects. Has no
	 * effect if exceptIfDeleted is set.
	 * @param exceptIfMissing throw an exception if the object does not exist
	 * in the database.
	 * @return a mapping of object id -> subdata paths -> data.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend occurs.
	 * @throws CorruptWorkspaceDBException if database corruption is detected.
	 * @throws TypedObjectExtractionException if the subdata could not be
	 * extracted.
	 */
	public Map<ObjectIDResolvedWS, Map<ObjectPaths, WorkspaceObjectData>>
			getObjects(
					Map<ObjectIDResolvedWS, Set<ObjectPaths>> objects,
					boolean noData,
					boolean exceptIfDeleted,
					boolean includeDeleted,
					boolean exceptIfMissing)
			throws NoSuchObjectException,WorkspaceCommunicationException,
			CorruptWorkspaceDBException, TypedObjectExtractionException;
	
	/** Get the set of outgoing references for an object.
	 * @param objs the objects for which to retrieve references.
	 * @param exceptIfDeleted throw an exception if the object is deleted if
	 * true.
	 * @param includeDeleted include references from deleted objects. Has no
	 * effect if exceptIfDeleted is set.
	 * @param exceptIfMissing throw and exception if the object does not
	 * exist if true. If true, the object will not exist in the returned map.
	 * @return the set of references for each object.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend occurs.
	 */
	public Map<ObjectIDResolvedWS, ObjectReferenceSet>
			getObjectOutgoingReferences(
					Set<ObjectIDResolvedWS> objs,
					boolean exceptIfDeleted,
					boolean includeDeleted,
					boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	public Map<ObjectIDResolvedWS, Set<ObjectInformation>>
			getReferencingObjects(PermissionSet perms,
					Set<ObjectIDResolvedWS> objs)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	/** @deprecated */
	public Map<ObjectIDResolvedWS, Integer> getReferencingObjectCounts(
			Set<ObjectIDResolvedWS> objects)
			throws WorkspaceCommunicationException, NoSuchObjectException;
	
	/** Get information about a set of objects.
	 * @param objectIDs the object IDs for which to retrieve information.
	 * @param includeMetadata true to return user supplied metadata with the
	 * information.
	 * @param exceptIfDeleted throw an exception if the object is in a
	 * deleted state.
	 * @param includeDeleted include information from deleted objects. Has no
	 * effect if exceptIfDeleted is set.
	 * @param exceptIfMissing throw an exception if the object does not exist
	 * in the database.
	 * @return a mapping of the object ID to information about the object.
	 * @throws NoSuchObjectException if the object doesn't exist.
	 * @throws WorkspaceCommunicationException if a communication error occurs
	 * with the backend database.
	 */
	public Map<ObjectIDResolvedWS, ObjectInformation> getObjectInformation(
			Set<ObjectIDResolvedWS> objectIDs,
			boolean includeMetadata,
			boolean exceptIfDeleted,
			boolean includeDeleted,
			boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	public Map<ObjectIDResolvedWS, TypeAndReference> getObjectType(
			final Set<ObjectIDResolvedWS> objectIDs) throws
			NoSuchObjectException, WorkspaceCommunicationException;

	public ObjectInformation copyObject(WorkspaceUser user, 
			ObjectIDResolvedWS from, ObjectIDResolvedWS to)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	public ObjectInformation revertObject(WorkspaceUser user,
			ObjectIDResolvedWS target)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	/** Get object names based on a provided name prefix. Returns at most 1000
	 * names in no particular order. Intended for use as an auto-completion
	 * method.
	 * @param rwsis the workspaces in which to look for names.
	 * @param prefix the prefix returned names must have.
	 * @param includeHidden include hidden objects in the output.
	 * @param limit the maximum number of names to return, at most 1000.
	 * @return map of workspace to a list of workspace names.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend occurs
	 */
	public Map<ResolvedWorkspaceID, List<String>> getNamesByPrefix(
			Set<ResolvedWorkspaceID> rwsis, String prefix,
			boolean includeHidden, int limit)
			throws WorkspaceCommunicationException;
	
	public WorkspaceInformation renameWorkspace(WorkspaceUser user,
			ResolvedWorkspaceID wsid, String newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public ObjectInformation renameObject(
			ObjectIDResolvedWS object, String newname)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	public void setObjectsHidden(Set<ObjectIDResolvedWS> objectIDs,
			boolean hide) throws NoSuchObjectException,
			WorkspaceCommunicationException;
	
	public void setObjectsDeleted(Set<ObjectIDResolvedWS> objectIDs,
			boolean delete) throws NoSuchObjectException,
			WorkspaceCommunicationException;

	public void setWorkspaceDeleted(ResolvedWorkspaceID wsid, boolean delete)
			throws WorkspaceCommunicationException;
	
	public List<WorkspaceInformation> getWorkspaceInformation(
			PermissionSet pset, List<WorkspaceUser> owners,
			WorkspaceUserMetadata meta, Date after, Date before,
			boolean showDeleted, boolean showOnlyDeleted)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public WorkspaceUser getWorkspaceOwner(ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public List<ObjectInformation> getObjectInformation(
			GetObjectInformationParameters perms)
			throws WorkspaceCommunicationException;

	public Map<ObjectIDResolvedWS, Boolean> getObjectExists(
			Set<ObjectIDResolvedWS> objectIDs)
			throws WorkspaceCommunicationException;
	
	public List<ObjectInformation> getObjectHistory(
			ObjectIDResolvedWS objectIDResolvedWS)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	public Set<WorkspaceUser> getAllWorkspaceOwners()
			throws WorkspaceCommunicationException;
	
	public boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException;

	public Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException;

	public void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;

	public void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;
	
	public TempFilesManager getTempFilesManager();

	public void setResourceUsageConfiguration(
			ResourceUsageConfiguration rescfg);
	
	/** Returns the status of the databases' dependencies.
	 * @return the dependency status.
	 */
	public List<DependencyStatus> status();
}
