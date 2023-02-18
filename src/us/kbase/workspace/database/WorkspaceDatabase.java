package us.kbase.workspace.database;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.DynamicConfig.DynamicConfigUpdate;
import us.kbase.workspace.database.ListObjectsParameters.ResolvedListObjectParameters;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoObjectDataException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

public interface WorkspaceDatabase {

	//TODO CODE return workspace info instead of resolved WS ID? Almost the same info. Switch to global read boolean on WS first.
	/** Resolve a workspace identifier.
	 * @param wsi the workspace identifier.
	 * @return the resolved identifier.
	 * @throws NoSuchWorkspaceException if the workspace is deleted or doesn't exist.
	 * @throws WorkspaceCommunicationException if an error occurred while communicating with the
	 * storage system.
	 */
	ResolvedWorkspaceID resolveWorkspace(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	/** Resolve a set of workspace identifiers.
	 * @param wsis the workspace identifiers.
	 * @return the resolved workspace identifiers.
	 * @throws NoSuchWorkspaceException if any of the workspaces are deleted or don't exist.
	 * @throws WorkspaceCommunicationException if an error occurred while communicating with the
	 * storage system.
	 */
	Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			Set<WorkspaceIdentifier> wsis)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	/** Resolve a workspace identifier.
	 *
	 * WARNING - may return deleted workspaces. There is no guarantee how long deleted workspaces
	 * may remain in the system, and attempting to access them again may result in an exception.
	 *
	 * @param wsi the workspace identifier.
	 * @param allowDeleted allow the target workspace to be in the deleted state.
	 * @return the resolved identifier.
	 * @throws NoSuchWorkspaceException if the workspace doesn't exist or if allowDeleted is false
	 * and the workspace is deleted.
	 * @throws WorkspaceCommunicationException if an error occurred while communicating with the
	 * storage system.
	 */
	ResolvedWorkspaceID resolveWorkspace(
			WorkspaceIdentifier wsi,
			boolean allowDeleted)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	/** Resolve a set of workspace identifiers.
	 *
	 * WARNING - may return deleted workspaces. There is no guarantee how long deleted workspaces
	 * may remain in the system, and attempting to access them again may result in an exception.
	 *
	 * @param wsis the workspace identifiers.
	 * @param suppressErrors if true, deleted workspaces will be returned in the results, and
	 * workspace identifiers that specify non-existent workspaces will be ignored. If false,
	 * errors will be thrown for either case.
	 * @return resolved workspace identifiers.
	 * @throws NoSuchWorkspaceException if suppressErrors is false and any of the specified
	 * workspaces don't exist or are deleted.
	 * @throws WorkspaceCommunicationException if an error occurred while communicating with the
	 * storage system.
	 */
	Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
			Set<WorkspaceIdentifier> wsis,
			boolean suppressErrors)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException;

	WorkspaceInformation createWorkspace(WorkspaceUser owner,
			String wsname, boolean globalread, String description,
			WorkspaceUserMetadata meta) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;

	/** Sets metadata for a workspace, overwriting existing keys if a
	 * duplicate key is supplied.
	 *
	 * @param wsid the workspace for which metadata will be altered.
	 * @param meta the metadata to add to the workspace.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 * @throws IllegalArgumentException if no metadata is supplied or the
	 * updated metadata exceeds the allowed size.
	 */
	Instant setWorkspaceMeta(ResolvedWorkspaceID wsid, WorkspaceUserMetadata meta)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Remove a metadata key from a workspace.
	 * @param wsid the workspace for which metadata will be altered.
	 * @param key the key to remove from the metadata.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 */
	Instant removeWorkspaceMetaKey(ResolvedWorkspaceID wsid, String key)
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
	WorkspaceInformation cloneWorkspace(
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

	/** Lock a workspace, preventing further modifications other than making the workspace
	 * publicly readable.
	 * @param wsid the workspace.
	 * @return the modification date of the workspace.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 */
	Instant lockWorkspace(ResolvedWorkspaceID wsid)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Set permissions on a workspace.
	 * @param rwsi the workspace to alter.
	 * @param users the users for which the permission will be set.
	 * @param perm the permission to set.
	 * @return the workspace modification date.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 */
	Instant setPermissions(
			ResolvedWorkspaceID rwsi,
			List<WorkspaceUser> users,
			Permission perm)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Change a workspace's owner.
	 * @param rwsi the workspace.
	 * @param user the current owner.
	 * @param newUser the new owner.
	 * @param newName the new workspace name, or null if the name should not change.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 */
	Instant setWorkspaceOwner(
			ResolvedWorkspaceID rwsi,
			WorkspaceUser user,
			WorkspaceUser newUser,
			Optional<String> newName)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Set the global permission on a workspace - e.g. whether the workspace is readable by the
	 * public or not.
	 * @param rwsi the workspace.
	 * @param perm the new global permission for the workspace - either READ or NONE.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 */
	Instant setGlobalPermission(ResolvedWorkspaceID rwsi, Permission perm)
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
	Permission getPermission(WorkspaceUser user, ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get permissions for a workspace for one user. This method will also
	 *  return whether the workspace is globally readable.
	 *
	 * Will return permissions for deleted workspaces.
	 *
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspace will be returned.
	 * @param rwsi the workspace to check. Note that the workspace may not be in
	 * the output - this indicates the user has no permissions to the workspace.
	 * @return the user's and global users' permission for the workspace.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	PermissionSet getPermissions(WorkspaceUser user, ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get permissions for a set of workspaces for one user. If the user is
	 * null, only the global readability of the workspaces will be returned.
	 *
	 * Will return permissions for deleted workspaces.
	 *
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspaces will be returned.
	 * @param rwsis the workspaces to check. If empty, all workspaces
	 * to which the user has permission will be returned. Note that not all the workspaces in the
	 * set may be in the output - this indicates the user has no permissions to that workspace.
	 * @return the user's and global users' permission for the workspaces.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	PermissionSet getPermissions(WorkspaceUser user, Set<ResolvedWorkspaceID> rwsis)
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
	PermissionSet getPermissions(
			WorkspaceUser user,
			Permission perm,
			boolean excludeGlobalRead)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get permissions for a set of workspaces for one user.
	 *
	 * @param user the user for whom to get permissions. If the user is null,
	 * only the global readability of the workspaces will be returned.
	 * @param rwsis the list of workspaces to check. If empty, all workspaces
	 * to which the user has permission will be returned. Note that not all the workspaces in the
	 * set may be in the output - this indicates the user has no permissions to that workspace.
	 * @param perm the minimum permission required for a workspace to be
	 * included in the permission set. Minimum READ.
	 * @param excludeGlobalRead exclude globally readable workspaces.
	 * @param excludeDeletedWorkspaces exclude deleted workspaces. Deleted
	 * workspaces in the supplied list are not affected.
	 * @param includeProvidedWorkspaces if true, include all the workspaces in the rwsis parameter
	 * regardless of whether the user has access or not. Will include workspaces with the NONE
	 * permission, unlike the default behavior.
	 * @return a set of permissions to workspaces for a user.
	 * @throws WorkspaceCommunicationException if a communication error occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	PermissionSet getPermissions(
			WorkspaceUser user,
			Set<ResolvedWorkspaceID> rwsis,
			Permission perm,
			boolean excludeGlobalRead,
			boolean excludeDeletedWorkspaces,
			boolean includeProvidedWorkspaces)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Returns all users' permissions for a set of workspaces */
	Map<ResolvedWorkspaceID, Map<User, Permission>> getAllPermissions(
			Set<ResolvedWorkspaceID> rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get information about a workspace.
	 * @param user the user that is requesting the information.
	 * @param rwsi the workspace.
	 * @return the workspace information.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage
	 * system.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 */
	WorkspaceInformation getWorkspaceInformation(
			WorkspaceUser user,
			ResolvedWorkspaceID rwsi)
			throws CorruptWorkspaceDBException, WorkspaceCommunicationException;

	/** Set or change the workspace description.
	 * @param wsid the workspace to modify.
	 * @param description the workspace description.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 */
	Instant setWorkspaceDescription(ResolvedWorkspaceID wsid, String description)
			throws WorkspaceCommunicationException;

	String getWorkspaceDescription(ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Save objects to a workspace. Note that any references passed into this method as part of
	 * a ResolvedSaveObject are assumed to be correct.
	 * @param user the workspace user that is saving the objects.
	 * @param rwsi the workspace to which the objects will be saved.
	 * @param objects the objects to be saved.
	 * @return information about each new object.
	 * @throws WorkspaceCommunicationException if a communication exception with the backend
	 * occurs.
	 * @throws NoSuchObjectException if the object id specified to save over does not exist.
	 */
	List<ObjectInformation> saveObjects(
			WorkspaceUser user,
			ResolvedWorkspaceID rwsi,
			List<ResolvedSaveObject> objects)
			throws WorkspaceCommunicationException, NoSuchObjectException;

	/** Get object and provenance information from the workspace database. The object data
	 * is not included, but can be added if desired with
	 * {@link #addDataToObjects(Collection, ByteArrayFileCacheManager, int)}.
	 * This allows for checking for constraints on returned data prior to retrieving it.
	 * @param objects the objects for which to retrieve information.
	 * @param exceptIfDeleted throw an exception if deleted.
	 * @param includeDeleted include information from deleted objects. Has no
	 * effect if exceptIfDeleted is set.
	 * @param exceptIfMissing throw an exception if the object does not exist
	 * in the database.
	 * @return a mapping of object id to object information builder. The information is returned
	 * as a builder so that copy status, reference paths, and object data can be updated as
	 * necessary.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend occurs.
	 */
	Map<ObjectIDResolvedWS, WorkspaceObjectData.Builder> getObjects(
					Set<ObjectIDResolvedWS> objects,
					boolean exceptIfDeleted,
					boolean includeDeleted,
					boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** Adds object data to the given builder, specifically calling
	 * {@link WorkspaceObjectData.Builder#withData(ByteArrayFileCacheManager.ByteArrayFileCache)},
	 * and taking the contents of {@link WorkspaceObjectData.Builder#getSubsetSelection()}
	 * into account. If this method throws an exception any data in the objects will be removed
	 * and destroyed.
	 *
	 * Up to twice the size of the objects may be needed to store the objects themselves and their
	 * subsets. How that data is stored is determined by the data manager.
	 *
	 * @param objects the object builders to update.
	 * @param dataManager the data manager.
	 * @param backendScaling the number of threads to use when fetching object data from the
	 * backend. For backends where the object data cannot be fetched in a batch this could
	 * significantly speed up data fetching. Details depend on the backend implementation
	 * and the environment in which the system is run.
	 * @throws WorkspaceCommunicationException if a communication error with the backend occurs.
	 * @throws TypedObjectExtractionException if the subdata could not be extracted.
	 * @throws NoObjectDataException if there is no data in the backend for the corresponding
	 * object.
	 * @throws InterruptedException if the thread is interrupted.
	 */
	void addDataToObjects(
			final Collection<WorkspaceObjectData.Builder> objects,
			final ByteArrayFileCacheManager dataManager,
			final int backendScaling)
			throws WorkspaceCommunicationException, TypedObjectExtractionException,
				NoObjectDataException, InterruptedException;

	/** Resolve a set of objects to absolute references. If the object cannot be found, it is not
	 * included in the returned map. Includes deleted objects.
	 * @param objects the objects to resolve.
	 * @return a map of the objects to their resolved references.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend occurs.
	 */
	Map<ObjectIDResolvedWS, Reference> getObjectReference(Set<ObjectIDResolvedWS> objects)
			throws WorkspaceCommunicationException;

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
	Map<ObjectIDResolvedWS, ObjectReferenceSet> getObjectOutgoingReferences(
					Set<ObjectIDResolvedWS> objs,
					boolean exceptIfDeleted,
					boolean includeDeleted,
					boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** Get the set of incoming references for an object. The object referred to by the reference
	 * is not checked for existence - if the reference does not exist, the reference set of
	 * incoming references will be empty. Includes deleted objects.
	 * @param objs the objects for which to retrieve references.
	 * @return the set of references for each object.
	 * @throws WorkspaceCommunicationException  if a communication error with the backend occurs.
	 */
	Map<Reference, ObjectReferenceSet> getObjectIncomingReferences(
			Set<Reference> objs) throws WorkspaceCommunicationException;

	Map<ObjectIDResolvedWS, Set<ObjectInformation>>
			getReferencingObjects(PermissionSet perms,
					Set<ObjectIDResolvedWS> objs)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** @deprecated */
	Map<ObjectIDResolvedWS, Integer> getReferencingObjectCounts(
			Set<ObjectIDResolvedWS> objects)
			throws WorkspaceCommunicationException, NoSuchObjectException;

	/** Get information about a set of objects.
	 *
	 * Note that the reference path provided is always simply the reference of the object.
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
	Map<ObjectIDResolvedWS, ObjectInformation> getObjectInformation(
			Set<ObjectIDResolvedWS> objectIDs,
			boolean includeMetadata,
			boolean exceptIfDeleted,
			boolean includeDeleted,
			boolean exceptIfMissing)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** Get the type of an object. Includes an absolute reference for each object.
	 * @param objectIDs the object for which the type should be returned.
	 * @param ignoreErrors ignore missing objects and include deleted objects.
	 * @return a type and reference for each object.
	 * @throws NoSuchObjectException if an object does not exist.
	 * @throws WorkspaceCommunicationException if a communication error with the backend occurs.
	 */
	Map<ObjectIDResolvedWS, TypeAndReference> getObjectType(
			final Set<ObjectIDResolvedWS> objectIDs,
			final boolean ignoreErrors) throws
			NoSuchObjectException, WorkspaceCommunicationException;

	/** Copies an object. If a version is not specified in the from argument and the object
	 * specified by the to argument does not exist, all the versions of the from argument are
	 * copied. Otherwise, only the version specified (or the most recent version) is
	 * copied. Note that it is an error to specify an object id in the to argument that does not
	 * exist.
	 * @param user the user performing the copy.
	 * @param from the copy source.
	 * @param to the copy target.
	 * @return the results of the copy.
	 * @throws NoSuchObjectException if the from object does not exist or the to object id does not
	 * exist.
	 * @throws WorkspaceCommunicationException if a communication error occurs with the storage
	 * system.
	 */
	CopyResult copyObject(
			WorkspaceUser user,
			ObjectIDResolvedWS from,
			ObjectIDResolvedWS to)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	ObjectInformation revertObject(WorkspaceUser user,
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
	Map<ResolvedWorkspaceID, List<String>> getNamesByPrefix(
			Set<ResolvedWorkspaceID> rwsis, String prefix,
			boolean includeHidden, int limit)
			throws WorkspaceCommunicationException;

	/** Rename a workspace.
	 * @param wsid the workspace.
	 * @param newname the new name for the workspace.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the storage system occurs
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	Instant renameWorkspace(ResolvedWorkspaceID wsid, String newname)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	ObjectInfoWithModDate renameObject(ObjectIDResolvedWS object, String newname)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** Hide or unhide objects.
	 * @param objectIDs the objects to hide.
	 * @param hide true to hide the object, false to unhide.
	 * @return the resolved objects mapped to the time of hiding.
	 * @throws NoSuchObjectException if an object doesn't exist.
	 * @throws WorkspaceCommunicationException if a communication error occurs with the storage
	 * system.
	 */
	Map<ResolvedObjectIDNoVer, Instant> setObjectsHidden(
			Set<ObjectIDResolvedWS> objectIDs,
			boolean hide)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	/** Delete or undelete objects.
	 * @param objectIDs the objects to delete.
	 * @param delete true to delete the object, false to undelete.
	 * @return the resolved objects mapped to the time of their deletion.
	 * @throws NoSuchObjectException if an object doesn't exist.
	 * @throws WorkspaceCommunicationException if a communication error occurs with the storage
	 * system.
	 */
	Map<ResolvedObjectIDNoVer, Instant> setObjectsDeleted(
			Set<ObjectIDResolvedWS> objectIDs,
			boolean delete)
			throws NoSuchObjectException,
			WorkspaceCommunicationException;

	/** Delete or undelete a workspace.
	 * @param wsid the workspace ID.
	 * @param delete true to delete the workspace, false to undelete it.
	 * @return the workspace modification time.
	 * @throws WorkspaceCommunicationException if a communication exception occurs.
	 */
	Instant setWorkspaceDeleted(ResolvedWorkspaceID wsid, boolean delete)
			throws WorkspaceCommunicationException;

	List<WorkspaceInformation> getWorkspaceInformation(
			PermissionSet pset, List<WorkspaceUser> owners,
			WorkspaceUserMetadata meta, Date after, Date before,
			boolean showDeleted, boolean showOnlyDeleted)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	WorkspaceUser getWorkspaceOwner(ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Get information about objects in a set of workspaces
	 * @param params the parameters for getting the objects.
	 * @return the object information
	 * @throws WorkspaceCommunicationException if a communication exception occurs.
	 */
	List<ObjectInformation> getObjectInformation(
			ResolvedListObjectParameters params)
			throws WorkspaceCommunicationException;

	/** Verify that a set of objects exist in the database and are not in
	 * the deleted state.
	 * @param objectIDs the objects to check.
	 * @return a map of objects to their state of existence in the database.
	 * @throws WorkspaceCommunicationException if a communication exception
	 * occurs.
	 */
	Map<ObjectIDResolvedWS, Boolean> getObjectExists(
			Set<ObjectIDResolvedWS> objectIDs)
			throws WorkspaceCommunicationException;

	/** Verify that a set of objects as specified by absolute references exist and are not deleted.
	 * This method does not verify the version exists.
	 * @param refs the objects to check.
	 * @return a mapping of each object to its state of existence / deletion.
	 * @throws WorkspaceCommunicationException if a communication exception occurs.
	 */
	Map<Reference, Boolean> getObjectExistsRef(Set<Reference> refs)
			throws WorkspaceCommunicationException;

	List<ObjectInformation> getObjectHistory(
			ObjectIDResolvedWS objectIDResolvedWS)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	Set<WorkspaceUser> getAllWorkspaceOwners()
			throws WorkspaceCommunicationException;

	boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException;

	Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException;

	void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;

	void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;

	/** Returns the status of the databases' dependencies.
	 * @return the dependency status.
	 */
	List<DependencyStatus> status();

	/** Get the dynamic configuration stored in the database.
	 * @return the config.
	 * @throws WorkspaceCommunicationException if a communication exception occurs.
	 * @throws CorruptWorkspaceDBException if the workspace database is corrupt.
	 */
	DynamicConfig getConfig() throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	/** Set the dynamic configuration in the database.
	 * @param config the configuration to set.
	 * @param overwrite true to overwrite any existing configuration, false to only set
	 * configuration values that don't already exist.
	 * @throws WorkspaceCommunicationException if a communication exception occurs.
	 */
	void setConfig(DynamicConfigUpdate config, boolean overwrite)
			throws WorkspaceCommunicationException;
}
