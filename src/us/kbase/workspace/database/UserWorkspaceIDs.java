package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.noNulls;
import static us.kbase.workspace.database.Util.nonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Optional;

/** A set of workspace IDs to which a user has permission.
 * @author gaprice@lbl.gov
 *
 */
public class UserWorkspaceIDs {
	
	final private Set<Long> workspaceIDs;
	final private Set<Long> publicWorkspaceIDs;
	final private Optional<WorkspaceUser> user;
	final private Permission perm;
	
	/** Create a set of workspace IDs.
	 * @param user the user that can access the workspaces, or null if only public workspaces
	 * are are included.
	 * @param perm the minimum permission that the user possesses for the workspaces.
	 * @param workspaceIDs the workspaces to which the user has explicit access.
	 * @param publicWorkspaceIDs workspaces to which the user has access only because they're
	 * public.
	 */
	public UserWorkspaceIDs(
			final WorkspaceUser user,
			final Permission perm,
			final Collection<Long> workspaceIDs,
			final Collection<Long> publicWorkspaceIDs) {
		this.user = Optional.fromNullable(user);
		nonNull(perm, "perm");
		this.perm = perm;
		nonNull(workspaceIDs, "workspaceIDs");
		noNulls(workspaceIDs, "null item in workspaceIDs");
		this.workspaceIDs = Collections.unmodifiableSet(new TreeSet<>(workspaceIDs));
		nonNull(publicWorkspaceIDs, "publicWorkspaceIDs");
		noNulls(publicWorkspaceIDs, "null item in publicWorkspaceIDs");
		this.publicWorkspaceIDs = Collections.unmodifiableSet(new TreeSet<>(publicWorkspaceIDs));
	}

	/** Get the IDs of the workspaces for which the user has explicit access.
	 * The underlying set implementation is a TreeSet.
	 * @return the workspace IDs.
	 */
	public Set<Long> getWorkspaceIDs() {
		return workspaceIDs;
	}

	/** Return the IDs of the workspaces for which the user has only public access.
	 * The underlying set implementation is a TreeSet.
	 * @return the workspace IDs.
	 */
	public Set<Long> getPublicWorkspaceIDs() {
		return publicWorkspaceIDs;
	}

	/** Get the user or absent if only public workspaces were included.
	 * @return the user.
	 */
	public Optional<WorkspaceUser> getUser() {
		return user;
	}

	/** Get the minimum permission of the workspaces.
	 * @return
	 */
	public Permission getPerm() {
		return perm;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("UserWorkspaceIDs [workspaceIDs=");
		builder.append(workspaceIDs);
		builder.append(", publicWorkspaceIDs=");
		builder.append(publicWorkspaceIDs);
		builder.append(", user=");
		builder.append(user);
		builder.append(", perm=");
		builder.append(perm);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((perm == null) ? 0 : perm.hashCode());
		result = prime * result + ((publicWorkspaceIDs == null) ? 0 : publicWorkspaceIDs.hashCode());
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		result = prime * result + ((workspaceIDs == null) ? 0 : workspaceIDs.hashCode());
		return result;
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
		UserWorkspaceIDs other = (UserWorkspaceIDs) obj;
		if (perm != other.perm) {
			return false;
		}
		if (publicWorkspaceIDs == null) {
			if (other.publicWorkspaceIDs != null) {
				return false;
			}
		} else if (!publicWorkspaceIDs.equals(other.publicWorkspaceIDs)) {
			return false;
		}
		if (user == null) {
			if (other.user != null) {
				return false;
			}
		} else if (!user.equals(other.user)) {
			return false;
		}
		if (workspaceIDs == null) {
			if (other.workspaceIDs != null) {
				return false;
			}
		} else if (!workspaceIDs.equals(other.workspaceIDs)) {
			return false;
		}
		return true;
	}
}
