package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.lib.ResolvedSaveObject;
import us.kbase.workspace.lib.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;

public interface WorkspaceDatabase {
	
	public String getBackendType();
	
	public TypedObjectValidator getTypeValidator();
	
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
			Map<String, String> meta) throws
			PreExistingWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException;
	
	public void setWorkspaceMetaKey(ResolvedWorkspaceID wsid,
			Map<String, String> meta)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public void removeWorkspaceMetaKey(ResolvedWorkspaceID wsid, String key)
			throws WorkspaceCommunicationException;
	
	public WorkspaceInformation cloneWorkspace(WorkspaceUser user,
			ResolvedWorkspaceID wsid, String newname, boolean globalread,
			String description, Map<String, String> meta)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public WorkspaceInformation lockWorkspace(WorkspaceUser user,
			ResolvedWorkspaceID wsid)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public void setPermissions(ResolvedWorkspaceID rwsi,
			List<WorkspaceUser> users, Permission perm) throws
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public void setGlobalPermission(ResolvedWorkspaceID rwsi, Permission perm)
			throws 	WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public Permission getPermission(WorkspaceUser user,
			ResolvedWorkspaceID rwsi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public PermissionSet getPermissions(WorkspaceUser user,
			ResolvedWorkspaceID rwsi) throws 
			WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public PermissionSet getPermissions(WorkspaceUser user,
			Set<ResolvedWorkspaceID> rwsis)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public PermissionSet getPermissions(WorkspaceUser user,
			Set<ResolvedWorkspaceID> rwsis, Permission perm,
			boolean excludeGlobalRead)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;
	
	public Map<User, Permission> getAllPermissions(
			ResolvedWorkspaceID rwsi) throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException;

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
	
	public Map<ObjectIDResolvedWS, Map<ObjectPaths, WorkspaceObjectData>> getObjects(
			Set<ObjectIDResolvedWS> objectIDs) throws NoSuchObjectException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public Map<ObjectIDResolvedWS, Map<ObjectPaths, WorkspaceObjectData>> getObjects(
			final Map<ObjectIDResolvedWS, Set<ObjectPaths>> objects)
			throws NoSuchObjectException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException, TypedObjectExtractionException;
	
	public Map<ObjectChainResolvedWS, WorkspaceObjectData> getReferencedObjects(
			Set<ObjectChainResolvedWS> values)
			throws NoSuchObjectException, WorkspaceCommunicationException,
			NoSuchReferenceException, CorruptWorkspaceDBException;
	
	public Map<ObjectIDResolvedWS, Set<ObjectInformation>>
			getReferencingObjects(PermissionSet perms,
					Set<ObjectIDResolvedWS> objs)
			throws NoSuchObjectException, WorkspaceCommunicationException;
	
	public Map<ObjectIDResolvedWS, Integer> getReferencingObjectCounts(
			Set<ObjectIDResolvedWS> objects)
			throws WorkspaceCommunicationException, NoSuchObjectException;
	
	public Map<ObjectIDResolvedWS, ObjectInformation> getObjectInformation(
			Set<ObjectIDResolvedWS> objectIDs, boolean includeMetadata,
			boolean ignoreMissingAndDeleted)
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

	public PermissionSet getPermissions(WorkspaceUser user,
			Permission perm, boolean excludeGlobal)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public List<WorkspaceInformation> getWorkspaceInformation(
			PermissionSet pset, List<WorkspaceUser> owners,
			Map<String, String> meta, Date after, Date before,
			boolean showDeleted, boolean showOnlyDeleted)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException;

	public List<ObjectInformation> getObjectInformation(
			PermissionSet pset, TypeDefId type, List<WorkspaceUser> savers,
			Map<String, String> meta, Date after, Date before,
			boolean showHidden, boolean showDeleted, boolean showOnlyDeleted,
			boolean showAllVers, boolean includeMetaData, int skip, int limit)
			throws WorkspaceCommunicationException;

	public List<ObjectInformation> getObjectHistory(
			ObjectIDResolvedWS objectIDResolvedWS)
			throws NoSuchObjectException, WorkspaceCommunicationException;

	public boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException;

	public Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException;

	public void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;

	public void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException;
	
	public TempFilesManager getTempFilesManager();

	public long getMaxObjectSize();

	public void setMaxObjectSize(long maxObjectSize);
	
	public long getMaxReturnSize();

	public void setMaxReturnSize(long maxReturnSize);

	public void setResourceUsageConfiguration(ResourceUsageConfiguration rescfg);
}
