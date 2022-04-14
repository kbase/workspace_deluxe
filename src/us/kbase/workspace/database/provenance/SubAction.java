package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Optional;

/** A {@link SubAction} is a provenance action taken as part of another action, for example,
 * running a separate piece of code, calling a remote API, etc. In most cases additional
 * information about these sub actions is not required to be recorded as the parent action
 * provides enough information to determine the sub action provenance. In some cases,
 * however, the sub action details can change over time even if the parent action details
 * remain the same, such as in the case of calling an external server at time t vs. time t+n.
 * In this case to fully reproduce the parent action the sub action details must be recorded, which
 * is the purpose of this class.
 *
 */
public class SubAction {

	private final String name;
	private final String ver;
	private final URL codeURL;
	private final String commit;
	private final URL endpointURL;

	private SubAction(
			final String name,
			final String ver,
			final URL codeURL,
			final String commit,
			final URL endpointURL) {
		this.name = name;
		this.ver = ver;
		this.codeURL = codeURL;
		this.commit = commit;
		this.endpointURL = endpointURL;
	}

	/** Get the name of the subaction, often the name of a code repo.
	 * @return the name, if any.
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/** Get the version of the subaction, often the version of code being used.
	 * @return the version, if any.
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(ver);
	}

	/** Get a URL pointing to the subaction's code, e.g. Github, Gitlab, etc.
	 * @return the URL, if any.
	 */
	public Optional<URL> getCodeURL() {
		return Optional.ofNullable(codeURL);
	}

	/** Get the code commit for the subaction's code.
	 * @return the commit, if any.
	 */
	public Optional<String> getCommit() {
		return Optional.ofNullable(commit);
	}

	/** Get the server endpoint URL for the subaction.
	 * @return the URL, if any.
	 */
	public Optional<URL> getEndpointURL() {
		return Optional.ofNullable(endpointURL);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((codeURL == null) ? 0 : codeURL.hashCode());
		result = prime * result + ((commit == null) ? 0 : commit.hashCode());
		result = prime * result + ((endpointURL == null) ? 0 : endpointURL.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((ver == null) ? 0 : ver.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SubAction other = (SubAction) obj;
		if (codeURL == null) {
			if (other.codeURL != null)
				return false;
		} else if (!codeURL.equals(other.codeURL))
			return false;
		if (commit == null) {
			if (other.commit != null)
				return false;
		} else if (!commit.equals(other.commit))
			return false;
		if (endpointURL == null) {
			if (other.endpointURL != null)
				return false;
		} else if (!endpointURL.equals(other.endpointURL))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (ver == null) {
			if (other.ver != null)
				return false;
		} else if (!ver.equals(other.ver))
			return false;
		return true;
	}
	
	/** Get a builder for a {@link SubAction}.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/** A {@link SubAction} builder. */
	public static class Builder {
		
		private String name = null;
		private String ver = null;
		private URL codeURL = null;
		private String commit = null;
		private URL endpointURL = null;
		
		private Builder() {};
		
		/** Set the name of the subaction, often the name of a code repo.
		 * @param name the name. Null or the empty string removes any current name in the builder.
		 * @return this builder.
		 */
		public Builder withName(final String name) {
			this.name = Common.processString(name);
			return this;
		}
		
		/** Set the version of the subaction, often the version of code being used.
		 * @param version the version. Null or the empty string removes any current version in
		 * the builder.
		 * @return this builder.
		 */
		public Builder withVersion(final String version) {
			this.ver = Common.processString(version);
			return this;
		}
		
		/** Set a URL pointing to the subaction's code, e.g. Github, Gitlab, etc.
		 * @param codeURL the code URL. Null or the empty string removes any current URL in
		 * the builder.
		 * @return this builder.
		 */
		public Builder withCodeURL(final String codeURL) {
			this.codeURL = Common.processURL(codeURL, "code");
			return this;
		}

		/** Set a URL pointing to the subaction's code, e.g. Github, Gitlab, etc.
		 * @param codeURL the code URL. Null removes any current URL in the builder.
		 * @return this builder.
		 */
		public Builder withCodeURL(final URL codeURL) {
			this.codeURL = Common.processURL(codeURL, "code");
			return this;
		}
		
		/** Set the code commit for the subaction's code.
		 * @param commit the commit. Null or the empty string removes any current commit in
		 * the builder.
		 * @return this builder.
		 */
		public Builder withCommit(final String commit) {
			this.commit = Common.processString(commit);
			return this;
		}
		
		/** Set the server endpoint URL for the subaction.
		 * @param endpointURL the endpoint URL. Null or the empty string removes any current URL
		 * in the builder.
		 * @return this builder.
		 */
		public Builder withEndpointURL(final String endpointURL) {
			this.endpointURL = Common.processURL(endpointURL, "endpoint");
			return this;
		}
		
		/** Set the server endpoint URL for the subaction.
		 * @param endpointURL the endpoint URL. Null removes any current URL in the builder.
		 * @return this builder.
		 */
		public Builder withEndpointURL(final URL endpointURL) {
			this.endpointURL = Common.processURL(endpointURL, "endpoint");
			return this;
		}
		
		/** Build the {@link SubAction}. At least one field must be populated.
		 * @return the sub action.
		 */
		public SubAction build() {
			if (
					name == null &&
					ver == null &&
					codeURL == null &&
					commit == null &&
					endpointURL == null) {
				throw new IllegalArgumentException(
						"At least one field in a provenance sub action must be provided");
			}
			return new SubAction(name, ver, codeURL, commit, endpointURL);
		}
	}
}
