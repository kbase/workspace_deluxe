package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.noNulls;
import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.MD5;

/** Various information about a workspace object, including the workspace and object names and
 * ids, the object version, they type, and more.
 * @author gaprice@lbl.gov
 *
 */
public class ObjectInformation {
	
	final private long id;
	final private String name;
	final private String type;
	final private Instant savedDate;
	final private int version;
	final private WorkspaceUser savedBy;
	final private long workspaceID;
	final private String workspaceName;
	final private String chksum;
	final private long size;
	final private UncheckedUserMetadata usermeta;
	final private UncheckedUserMetadata adminmeta;
	final private List<Reference> refpath;
	
	private ObjectInformation(
			final long id,
			final String name,
			final String typeString,
			final Instant savedDate,
			final int version,
			final WorkspaceUser savedBy,
			final long workspaceId,
			final String workspaceName,
			final String chksum,
			final long size,
			final UncheckedUserMetadata usermeta,
			final UncheckedUserMetadata adminmeta,
			final List<Reference> refpath) {
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.savedDate = savedDate;
		this.version = version;
		this.savedBy = savedBy;
		this.workspaceID = workspaceId;
		this.workspaceName = workspaceName;
		this.chksum = chksum;
		this.size = size;
		this.usermeta = usermeta;
		this.adminmeta = adminmeta;
		if (refpath == null) {
			//could leave this as null and construct as needed to save mem, but meh for now
			this.refpath = Arrays.asList(new Reference(workspaceId, id, version));
		} else {
			this.refpath = Collections.unmodifiableList(new LinkedList<>(refpath));
		}
	}
	
	/** Returns the id of the object.
	 * @return the id of the object.
	 */
	public long getObjectId() {
		return id;
	}
	
	/** Returns the name of the object.
	 * @return the name of the object.
	 */
	public String getObjectName() {
		return name;
	}
	
	/** Returns the type of the object as a string. This will always be the absolute type.
	 * @return the object type.
	 */
	public String getTypeString() {
		return type;
	}
	
	/** Returns the size of the object when serialized to a JSON string in bytes.
	 * @return the size of the object in bytes.
	 */
	public long getSize() {
		return size;
	}
	
	/** Returns the time the object was saved.
	 * @return the time the object was saved.
	 */
	public Instant getSavedDate() {
		return savedDate;
	}
	
	/** Returns the version of the object.
	 * @return the version of the object.
	 */
	public int getVersion() {
		return version;
	}
	
	/** Returns the user that saved or copied the object.
	 * @return the user that saved or copied the object.
	 */
	public WorkspaceUser getSavedBy() {
		return savedBy;
	}
	
	/** Returns the id of the workspace in which the object is stored.
	 * @return the workspace id.
	 */
	public long getWorkspaceId() {
		return workspaceID;
	}
	
	/** Returns the name of the workspace in which the object is stored.
	 * @return the workspace name.
	 */
	public String getWorkspaceName() {
		return workspaceName;
	}
	
	/** Returns the md5 checksum of the object when serialized to a JSON string with sorted keys.
	 * @return the md5 checksum.
	 */
	public String getCheckSum() {
		return chksum;
	}
	
	/** Returns the user supplied and automatically generated metadata for the object.
	 * @return the object metadata.
	 */
	public Optional<UncheckedUserMetadata> getUserMetaData() {
		return Optional.ofNullable(usermeta);
	}
	
	/** Returns the user supplied and automatically generated metadata for the object as a map.
	 * 
	 * @param nullIfEmpty return null rather than an empty map if there is no metadata available.
	 * @return the metadata or null.
	 */
	public Map<String, String> getUserMetaDataMap(final boolean nullIfEmpty) {
		return getMetadataMap(usermeta, nullIfEmpty);
	}

	private Map<String, String> getMetadataMap(
			final UncheckedUserMetadata meta,
			boolean nullIfEmpty) {
		if (meta == null) {
			return nullIfEmpty ? null : Collections.emptyMap();
		}
		return meta.getMetadata();
	}
	
	/** Returns the administrative user supplied metadata for the object.
	 * @return the object metadata.
	 */
	public Optional<UncheckedUserMetadata> getAdminUserMetaData() {
		return Optional.ofNullable(adminmeta);
	}
	
	/** Returns the administrative user supplied metadata for the object as a map.

	 * @param nullIfEmpty return null rather than an empty map if there is no metadata available.
	 * @return the metadata or null.
	 */
	public Map<String, String> getAdminUserMetaDataMap(final boolean nullIfEmpty) {
		return getMetadataMap(adminmeta, nullIfEmpty);
	}

	/** Returns the resolved reference path to this object from a user-accessible object. There may
	 * be more than one possible path to an object; only the path used to verify accessibility for
	 * the current access is provided. If the object is directly user-accessible the path will only
	 * contain the object reference.
	 * @return the reference path to the object.
	 */
	public List<Reference> getReferencePath() {
		return refpath;
	}
	
	/** Updates the reference path to the path supplied and returns a new ObjectInformation with
	 * that reference path.
	 * @param refpath the reference path that should replace the current reference path. The last
	 * entry in the reference path must be identical to the last entry in the current reference
	 * path.
	 * @return a new ObjectInformation with an updated reference path.
	 */
	public ObjectInformation updateReferencePath(final List<Reference> refpath) {
		if (refpath == null || refpath.isEmpty()) {
			throw new IllegalArgumentException("refpath cannot be null or empty");
		}
		noNulls(refpath, "refpath cannot contain nulls");
		if (!getLast(refpath).equals(getLast(this.refpath))) {
			throw new IllegalArgumentException(
					"refpath must end with the same reference as the current refpath");
		}
		return new ObjectInformation(id, name, type, savedDate, version, savedBy, workspaceID,
				workspaceName, chksum, size, usermeta, adminmeta, refpath);
	}
	
	private Reference getLast(final List<Reference> refpath) {
		return refpath.get(refpath.size() - 1);
	}

	@Override
	public int hashCode() {
		return Objects.hash(adminmeta, chksum, id, name, refpath, savedBy, savedDate, size, type,
				usermeta, version, workspaceID, workspaceName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ObjectInformation other = (ObjectInformation) obj;
		return Objects.equals(adminmeta, other.adminmeta)
				&& Objects.equals(chksum, other.chksum)
				&& id == other.id
				&& Objects.equals(name, other.name)
				&& Objects.equals(refpath, other.refpath)
				&& Objects.equals(savedBy, other.savedBy)
				&& Objects.equals(savedDate, other.savedDate)
				&& size == other.size
				&& Objects.equals(type, other.type)
				&& Objects.equals(usermeta, other.usermeta)
				&& version == other.version
				&& workspaceID == other.workspaceID
				&& Objects.equals(workspaceName, other.workspaceName);
	}
	
	/** Get a builder for an {@link ObjectInformation}.
	 * 
	 * Note that all fields other than the metadata are required, but all the fields are settable
	 * by builder methods for readability purposes.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/**
	 * A builder for an {@link ObjectInformation}.
	 * 
	 * Note that all fields other than the metadata are required, but all the fields are settable
	 * by builder methods for readability purposes.
	 */
	public static class Builder {
		
		// required fields
		private long objectId = -1;
		private String objectName = null;
		private AbsoluteTypeDefId type = null;
		private Instant savedDate = null;
		private int version = -1;
		private WorkspaceUser savedBy = null;
		private ResolvedWorkspaceID ws = null;
		private String chksum = null;
		private long size = -1;
		
		// optional fields
		private UncheckedUserMetadata usermeta = null;
		private UncheckedUserMetadata adminmeta = null;
		
		
		private Builder() {}
		
		/** Add the required object ID to the builder.
		 * @param id the object ID.
		 * @return this builder for chaining.
		 */
		public Builder withObjectID(final long id) {
			this.objectId = gt0(id, "id");
			return this;
		}
		
		private long gt0(final long num, final String name) {
			checkGt0(num, name);
			return num;
		}

		private int gt0(final int num, final String name) {
			checkGt0(num, name);
			return num;
		}

		private void checkGt0(final long num, final String name) {
			if (num < 1) {
				throw new IllegalArgumentException(String.format("%s must be > 0", name));
			}
		}
		
		/** Add the required object name to the builder.
		 * @param name the object name.
		 * @return this builder for chaining.
		 */
		public Builder withObjectName(final String name) {
			// could check the name via regex as well but since this class should only be
			// created internally... meh, waste of human and cpu cycles
			this.objectName = checkString(name, "name");
			return this;
		}
		
		/** Add the required type of the object to the builder.
		 * @param type the object type.
		 * @return this builder for chaining.
		 */
		public Builder withType(final AbsoluteTypeDefId type) {
			this.type = requireNonNull(type, "type");
			return this;
		}
		
		/** Add the required date the object was saved or copied to the builder.
		 * @param date the save / copy date.
		 * @return this builder for chaining.
		 */
		public Builder withSavedDate(final Instant date) {
			this.savedDate = requireNonNull(date, "date");
			return this;
		}
		
		/** Add the required date the object was saved or copied to the builder.
		 * @param date the save / copy date.
		 * @return this builder for chaining.
		 */
		public Builder withSavedDate(final Date date) {
			this.savedDate = requireNonNull(date, "date").toInstant();
			return this;
		}
		
		/** Adds the required version of the object to the builder.
		 * @param version the object version.
		 * @return this builder for chaining.
		 */
		public Builder withVersion(final int version) {
			this.version = gt0(version, "version");
			return this;
		}
		
		/** Adds the required user that saved or copied the object to the builder.
		 * @param user the user.
		 * @return this builder for chaining.
		 */
		public Builder withSavedBy(final WorkspaceUser user) {
			this.savedBy = requireNonNull(user, "user");
			return this;
		}
		
		/** Adds the required workspace information for the workspace containing the object
		 * to the builder.
		 * @param workspace the workspace ID.
		 * @return this builder for chaining.
		 */
		public Builder withWorkspace(final ResolvedWorkspaceID workspace) {
			this.ws = requireNonNull(workspace, "workspace");
			return this;
		}
		
		/** Add the required md5 checksum of the object when serialized to a JSON string with
		 * sorted keys to the builder.
		 * @param checksum the checksum
		 * @return this builder for chaining.
		 */
		public Builder withChecksum(final MD5 checksum) {
			this.chksum = requireNonNull(checksum, "checksum").getMD5();
			return this;
		}
		
		/** Add the required md5 checksum of the object when serialized to a JSON string with
		 * sorted keys to the builder.
		 * @param checksum the checksum
		 * @return this builder for chaining.
		 */
		public Builder withChecksum(final String checksum) {
			// again could do more checking here but should be built internally only so meh
			this.chksum = checkString(checksum, "checksum");
			return this;
		}

		/** Add the required size of the object when serialized to a JSON string to the builer.
		 * @param size the object size.
		 * @return this builder for chaining.
		 */
		public Builder withSize(final long size) {
			this.size = gt0(size, "size");
			return this;
		}

		/** Add the optional user provided and / or autogenerated metadata to the builder.
		 * 
		 * Empty metadata is treated the same way as a null argument.
		 * 
		 * A null argument will remove any previously set metadata.
		 * @param metadata the metadata
		 * @return this builder for chaining.
		 */
		public Builder withUserMetadata(final UncheckedUserMetadata metadata) {
			this.usermeta = metadata == null || metadata.isEmpty() ? null : metadata;
			return this;
		}
		
		/** Add the optional administrative user provided metadata to the builder.
		 * 
		 * Empty metadata is treated the same way as a null argument.
		 * 
		 * A null argument will remove any previously set metadata.
		 * @param metadata the metadata
		 * @return this builder for chaining.
		 */
		public Builder withAdminUserMetadata(final UncheckedUserMetadata metadata) {
			this.adminmeta = metadata == null || metadata.isEmpty() ? null : metadata;
			return this;
		}
		
		/** Create the {@link ObjectInformation}. All fields are required other than the metadata.
		 * @return the new {@link ObjectInformation}.
		 */
		public ObjectInformation build() {
			if (	objectId < 1 ||
					objectName == null ||
					type == null ||
					savedDate == null ||
					version < 1 ||
					savedBy == null ||
					ws == null ||
					chksum == null ||
					size < 1) {
				throw new IllegalArgumentException("One or more of the required arguments are "
						+ "not set. Please check the documentation for the builder.");
			}
			return new ObjectInformation(objectId, objectName, type.getTypeString(), savedDate,
					version, savedBy, ws.getID(), ws.getName(), chksum, size, usermeta, adminmeta,
					null);
		}
	}
	
}
