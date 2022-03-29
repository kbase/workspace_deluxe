package us.kbase.workspace.database.provenance;

import static us.kbase.workspace.database.Util.isNullOrEmpty;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;

/**
 * Information about data external to the workspace application. Typically used to provide
 * information about the source of data stored to the workspace.
 */
public class ExternalData {
	
	private final String resourceName;
	private final URL resourceURL;
	private final String resourceVersion;
	private final Instant resourceReleaseDate;
	private final URL dataURL;
	private final String dataID;
	private final String description;

	private ExternalData(
			final String resourceName,
			final URL resourceURL,
			final String resourceVersion,
			final Instant resourceReleaseDate,
			final URL dataURL,
			final String dataID,
			final String description) {
		this.resourceName = resourceName;
		this.resourceURL = resourceURL;
		this.resourceVersion = resourceVersion;
		this.resourceReleaseDate = resourceReleaseDate;
		this.dataURL = dataURL;
		this.dataID = dataID;
		this.description = description;
	}

	/** Get the name of the resource, for example JGI.
	 * @return the resource name, if present.
	 */
	public Optional<String> getResourceName() {
		return Optional.ofNullable(resourceName);
	}

	/** Get the url of the resource, for example http://genome.jgi.doe.gov.
	 * @return the url, if present.
	 */
	public Optional<URL> getResourceURL() {
		return Optional.ofNullable(resourceURL);
	}

	/** Get the version of the resource.
	 * @return the version, if present.
	 */
	public Optional<String> getResourceVersion() {
		return Optional.ofNullable(resourceVersion);
	}

	/** Get the release date of the resource.
	 * @return the resource release date, if present.
	 */
	public Optional<Instant> getResourceReleaseDate() {
		return Optional.ofNullable(resourceReleaseDate);
	}

	/** Get the url of the data, for example
	 * http://genome.jgi.doe.gov/pages/dynamicOrganismDownload.jsf?organism=BlaspURHD0036
	 * @return the data url, if present.
	 */
	public Optional<URL> getDataURL() {
		return Optional.ofNullable(dataURL);
	}

	/** Get the id of the data, for example 7625.2.79179.AGTTCC.adnq.fastq.gz.
	 * @return the data id, if present.
	 */
	public Optional<String> getDataID() {
		return Optional.ofNullable(dataID);
	}

	/** Get a free text description of the data.
	 * @return the description, if present.
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/** Get a builder for an {@link ExternalData}.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dataID == null) ? 0 : dataID.hashCode());
		result = prime * result + ((dataURL == null) ? 0 : dataURL.hashCode());
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((resourceName == null) ? 0 : resourceName.hashCode());
		result = prime * result + ((resourceReleaseDate == null) ? 0 : resourceReleaseDate.hashCode());
		result = prime * result + ((resourceURL == null) ? 0 : resourceURL.hashCode());
		result = prime * result + ((resourceVersion == null) ? 0 : resourceVersion.hashCode());
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
		ExternalData other = (ExternalData) obj;
		if (dataID == null) {
			if (other.dataID != null)
				return false;
		} else if (!dataID.equals(other.dataID))
			return false;
		if (dataURL == null) {
			if (other.dataURL != null)
				return false;
		} else if (!dataURL.equals(other.dataURL))
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (resourceName == null) {
			if (other.resourceName != null)
				return false;
		} else if (!resourceName.equals(other.resourceName))
			return false;
		if (resourceReleaseDate == null) {
			if (other.resourceReleaseDate != null)
				return false;
		} else if (!resourceReleaseDate.equals(other.resourceReleaseDate))
			return false;
		if (resourceURL == null) {
			if (other.resourceURL != null)
				return false;
		} else if (!resourceURL.equals(other.resourceURL))
			return false;
		if (resourceVersion == null) {
			if (other.resourceVersion != null)
				return false;
		} else if (!resourceVersion.equals(other.resourceVersion))
			return false;
		return true;
	}

	/** A builder for an {@link ExternalData}. */
	public static class Builder {
		
		private String resourceName = null;
		private URL resourceURL = null;
		private String resourceVersion = null;
		private Instant resourceReleaseDate = null;
		private URL dataURL = null;
		private String dataID = null;
		private String description = null;

		private Builder() {};
		
		/** Set the name of the resource, for example JGI.
		 * @param resourceName the name of the resource. Null or the empty string removes any
		 * current resource in the builder.
		 * @return this builder.
		 */
		public Builder withResourceName(final String resourceName) {
			this.resourceName = processString(resourceName);
			return this;
		}
		
		/** Set the url of the resource, for example http://genome.jgi.doe.gov.
		 * @param resourceURL the url of the resource. Null or the empty string removes any
		 * current url in the builder.
		 * @return this builder.
		 */
		public Builder withResourceURL(final String resourceURL) {
			this.resourceURL = processURL(resourceURL);
			return this;
		}

		/** Set the url of the resource, for example http://genome.jgi.doe.gov.
		 * @param resourceURL the url of the resource. Null removes any current url in the builder.
		 * @return this builder.
		 */
		public Builder withResourceURL(final URL resourceURL) {
			// TODO PROV integration error test
			this.resourceURL = processURL(resourceURL);
			return this;
		}
		
		/** Set version of the resource.
		 * @param resourceVersion the resource version. Null or the empty string removes any
		 * current version in the builder.
		 * @return this builder.
		 */
		public Builder withResourceVersion(final String resourceVersion) {
			this.resourceVersion = processString(resourceVersion);
			return this;
		}
		
		/** Set the release date of the resource.
		 * @param releaseDate the release date. Null removes any current date in the builder.
		 * @return this builder.
		 */
		public Builder withResourceReleaseDate(final Instant releaseDate) {
			this.resourceReleaseDate = releaseDate;
			return this;
		}
		
		/** Set the url of the data, for example
		 * http://genome.jgi.doe.gov/pages/dynamicOrganismDownload.jsf?organism=BlaspURHD0036
		 * @param dataURL the url of the data. Null or the empty string removes any
		 * current url in the builder.
		 * @return this builder.
		 */
		public Builder withDataURL(final String dataURL) {
			this.dataURL = processURL(dataURL);
			return this;
		}
		
		/** Set the url of the data, for example
		 * http://genome.jgi.doe.gov/pages/dynamicOrganismDownload.jsf?organism=BlaspURHD0036
		 * @param dataURL the url of the data. Null removes any current url in the builder.
		 * @return this builder.
		 */
		public Builder withDataURL(final URL dataURL) {
			// TODO PROV integration error test
			this.dataURL = processURL(dataURL);
			return this;
		}
		
		/** Set the ID of the data, for example 7625.2.79179.AGTTCC.adnq.fastq.gz.
		 * @param dataID the data ID. Null or the empty string removes any
		 * current data ID in the builder.
		 * @return this builder.
		 */
		public Builder withDataID(final String dataID) {
			this.dataID = processString(dataID);
			return this;
		}
		
		/** Set a free text description of the data.
		 * @param description the description. Null or the empty string removes any
		 * current description in the builder.
		 * @return this builder.
		 */
		public Builder withDescription(final String description) {
			this.description = processString(description);
			return this;
		}

		private String processString(final String input) {
			return isNullOrEmpty(input) ? null : input.trim();
		}

		private URL processURL(final String url) {
			return isNullOrEmpty(url) ? null : checkURL(url);
		}

		private URL processURL(final URL url) {
			return url == null ? null : checkURL(url);
		}
		
		private URL checkURL(final String putativeURL) {
			final URL url;
			try {
				url = new URL(putativeURL);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException(String.format(
						"Illegal url '%s': %s", putativeURL, e.getLocalizedMessage()), e);
			}
			return checkURL(url);
		}
		
		private URL checkURL(final URL url) {
			try {
				url.toURI();
				return url;
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(String.format(
						"Illegal url '%s': %s", url, e.getLocalizedMessage()), e);
			}
		}
		
		/** Build the {@link ExternalData}. At least one field must be populated.
		 * @return the external data.
		 */
		public ExternalData build() {
			if (
					resourceName == null &&
					resourceURL == null &&
					resourceVersion == null &&
					resourceReleaseDate == null &&
					dataURL == null &&
					dataID == null &&
					description == null) {
				// TODO PROV integration error test
				throw new IllegalArgumentException(
						"At least one field in an external data unit must be provided");
			}
			return new ExternalData(resourceName, resourceURL, resourceVersion,
					resourceReleaseDate, dataURL, dataID, description);
		}
	}
}
