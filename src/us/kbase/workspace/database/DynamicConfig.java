package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Represents the state of workspace configuration that can be updated dynamically. */
public class DynamicConfig {
	// This is a minimal implementation since there's only one configuration item currently.
	// May want to add more features / DRY things up later.
	
	// may want to add a means to get a subset of the config to save bandwidth, but for
	// now that's moot.

	/** The map key name for the backend scaling parameter. */
	public static final String KEY_BACKEND_SCALING = "backend-scaling";
	private static final int DEFAULT_BACKEND_SCALING = 1;
	private final int backendScaling;
	
	private DynamicConfig(final int backendScaling) {
		this.backendScaling = backendScaling;
	}
	
	/** Get the backend scaling parameter - e.g. how many simultaneous requests should be
	 * made to a file backend to retrieve data for workspace objects for a single object
	 * retrieval call.
	 * @return the scaling parameter.
	 */
	public Optional<Integer> getBackendScaling() {
		return backendScaling < 1 ? Optional.empty() : Optional.of(backendScaling);
	}
	
	/** Get the configuration as a map.
	 * @return the configuration map.
	 */
	public Map<String, Object> toMap() {
		final Map<String, Object> ret = new HashMap<>();
		if (backendScaling > 0) {
			ret.put(KEY_BACKEND_SCALING, backendScaling);
		}
		return ret;
	}
	
	private static int checkBackendScaling(final int backendScaling) {
		if (backendScaling < 1) {
			throw new IllegalArgumentException("backend scaling must be > 0");
		}
		return backendScaling;
	}
	
	private static int checkMapInteger(final Map<String, Object> configItems, final String key) {
		final Object value = configItems.get(key);
		if (!(value instanceof Integer) || (Integer) value < 1) {
			throw new IllegalArgumentException(String.format(
					"%s must be an integer > 0", key));
		}
		return (int) value;
	}
	
	private static class MapState {
		private int backendScaling = -1;
	}

	private static MapState checkMap(final Map<String, Object> configItems) {
		// don't mutate source
		final Map<String, Object> copy = new TreeMap<>(requireNonNull(configItems, "configItems"));
		final MapState ret = new MapState();
		if (copy.containsKey(KEY_BACKEND_SCALING)) {
			// may want to add handlers for each key, as this might get unwieldy with a lot
			// of config items, but since there's only one for now...
			ret.backendScaling = checkMapInteger(copy, KEY_BACKEND_SCALING);
			copy.remove(KEY_BACKEND_SCALING);
		}
		if (!copy.isEmpty()) {
			throw new IllegalArgumentException(String.format(
					"Unexpected key in configuration map: %s", copy.keySet().iterator().next()));
		}
		return ret;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + backendScaling;
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
		DynamicConfig other = (DynamicConfig) obj;
		if (backendScaling != other.backendScaling)
			return false;
		return true;
	}

	/** Get a builder for the configuration.
	 * @return the builder.
	 */
	public static Builder getBuilder() {
		return new Builder();
	}
	
	/** A configuration builder. */
	public static class Builder {

		private int backendScaling = -1;
		
		private Builder() {}
		
		/** Set the backend scaling parameter for the builder. This parameter determines how many
		 * simultaneous requests should be made to a file backend to retrieve data for
		 * workspace objects for a single object retrieval call.
		 * @param backendScaling the backend scaling parameter.
		 * @return this builder.
		 */
		public Builder withBackendScaling(final int backendScaling) {
			this.backendScaling = checkBackendScaling(backendScaling);
			return this;
		}
		
		/** Set the state of the builder from a map of configuration keys to configuration values.
		 * @param configItems the map of configuration items.
		 * @return this builder.
		 */
		public Builder withMap(final Map<String, Object> configItems) {
			final MapState ms = checkMap(configItems);
			this.backendScaling = ms.backendScaling > 0 ? ms.backendScaling : this.backendScaling;
			return this;
		}
		
		/** Build the configuration.
		 * @return the configuration.
		 */
		public DynamicConfig build() {
			return new DynamicConfig(backendScaling);
		}
	}
	
	/** Represents an update to the state of the dynamic configuration. */
	public static class DynamicConfigUpdate {
		// right now DCU and DC are basically the same, but we separate them here for the
		// future case where only a portion of config items might be altered or some
		// config items might be removed, which can't and shouldn't be represented in a regular
		// config class.
		
		// should never be removed
		private final int backendScaling;

		private DynamicConfigUpdate(final int backendScaling) {
			this.backendScaling = backendScaling;
		}
		
		/** Get the default configuration as an update.
		 * @return the default configuration update.
		 */
		public static DynamicConfigUpdate getDefault() {
			return new DynamicConfigUpdate(DEFAULT_BACKEND_SCALING);
		}
		
		/** Get a map of configuration key to configuration value to which the configuration
		 * should be set in storage. This may be a subset of the entire configuration.
		 * @return a map of how the configuration should be set.
		 */
		public Map<String, Object> toSet() {
			final Map<String, Object> ret = new HashMap<>();
			if (backendScaling > 0) {
				ret.put(KEY_BACKEND_SCALING, backendScaling);
			}
			return ret;
		}
		
		/** Get a set of configuration keys to remove from the configuration.
		 * @return the keys to remove.
		 */
		public Set<String> toRemove() {
			// at some point in the future this may do something
			return Collections.emptySet();
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + backendScaling;
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
			DynamicConfigUpdate other = (DynamicConfigUpdate) obj;
			if (backendScaling != other.backendScaling)
				return false;
			return true;
		}

		/** Get a builder for a configuration update.
		 * @return the builder.
		 */
		public static Builder getBuilder() {
			return new Builder();
		}
		
		/** A builder for a configuration update. */
		public static class Builder {

			private int backendScaling = -1;

			private Builder() {};

			/** Set the backend scaling parameter for the builder. This parameter determines how
			 * many simultaneous requests should be made to a file backend to retrieve data for
			 * workspace objects for a single object retrieval call.
			 * @param backendScaling the backend scaling parameter.
			 * @return this builder.
			 */
			public Builder withBackendScaling(final int backendScaling) {
				this.backendScaling = checkBackendScaling(backendScaling);
				return this;
			}

			/** Set the state of the builder from a map of configuration keys to configuration
			 * values.
			 * @param configItems the map of configuration items.
			 * @return this builder.
			 */
			public Builder withMap(final Map<String, Object> configItems) {
				final MapState ms = checkMap(configItems);
				this.backendScaling = ms.backendScaling > 0 ?
						ms.backendScaling : this.backendScaling;
				return this;
			}

			/** Build the configuration update.
			 * @return the configuration update.
			 */
			public DynamicConfigUpdate build() {
				return new DynamicConfigUpdate(backendScaling);
			}
		}
	}
}
