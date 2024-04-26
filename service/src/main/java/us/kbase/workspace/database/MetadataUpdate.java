package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.noNulls;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** An update to a a set of metadata, including keys to add or replace and keys to remove. */
public class MetadataUpdate {
	
	private final WorkspaceUserMetadata meta;
	private final Set<String> remove;
	
	/** Create the update.
	 * @param meta keys to add to or replace in the target metadata.
	 * @param toRemove keys to remove from the target metadata.
	 */
	public MetadataUpdate(final WorkspaceUserMetadata meta, final Collection<String> toRemove) {
		this.meta = meta == null || meta.isEmpty() ? null : meta;
		if (toRemove != null && !toRemove.isEmpty()) {
			this.remove = Collections.unmodifiableSet(new HashSet<>(
					noNulls(
							toRemove,
							"null metadata keys are not allowed in the remove parameter"
					)
			));
		} else {
			this.remove = null;
		}
	}

	/** Get the keys to add or replace in the target metadata.
	 * @return the keys.
	 */
	public Optional<WorkspaceUserMetadata> getMeta() {
		return Optional.ofNullable(meta);
	}

	/** Get the keys to remove from the target metadata.
	 * @return
	 */
	public Optional<Set<String>> getToRemove() {
		return Optional.ofNullable(remove);
	}

	/** Return whether this metadata update has an update.
	 * @return true if there are keys to add, replace, or remove in this update.
	 */
	public boolean hasUpdate() {
		return meta != null || remove != null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(meta, remove);
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
		MetadataUpdate other = (MetadataUpdate) obj;
		return Objects.equals(meta, other.meta) && Objects.equals(remove, other.remove);
	}
	
}
