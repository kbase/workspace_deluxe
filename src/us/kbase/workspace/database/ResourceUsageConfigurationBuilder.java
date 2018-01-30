package us.kbase.workspace.database;

/** Builder for the set of resource usage parameters for the workspace.
 * @author gaprice@lbl.gov
 *
 */
public class ResourceUsageConfigurationBuilder {
	
	//TODO TEST unit tests
	
	final public static int DEFAULT_MAX_OBJECT_SIZE = 1000000000;
	final public static int DEFAULT_MAX_INCOMING_DATA_MEMORY_USAGE = 100000000;
	final public static int DEFAULT_MAX_RELABEL_AND_SORT_MEMORY_USAGE =
			200000000; // must be at least 1x max data
	final public static int DEFAULT_MAX_RETURNED_DATA_MEMORY_USAGE = 300000000;
	final public static long DEFAULT_MAX_RETURNED_DATA_SIZE = 1000000000L;
	
	private int maxObjectSize;
	private int maxIncomingDataMemoryUsage;
	private int maxRelabelAndSortMemoryUsage;
	private int maxReturnedDataMemoryUsage;
	private long maxReturnedDataSize;
	
	public ResourceUsageConfigurationBuilder() {
		maxObjectSize = DEFAULT_MAX_OBJECT_SIZE;
		maxIncomingDataMemoryUsage = DEFAULT_MAX_INCOMING_DATA_MEMORY_USAGE;
		maxRelabelAndSortMemoryUsage = DEFAULT_MAX_RELABEL_AND_SORT_MEMORY_USAGE;
		maxReturnedDataMemoryUsage = DEFAULT_MAX_RETURNED_DATA_MEMORY_USAGE;
		maxReturnedDataSize = DEFAULT_MAX_RETURNED_DATA_SIZE;
	}
	
	public ResourceUsageConfigurationBuilder(ResourceUsageConfiguration cfg) {
		maxObjectSize = cfg.getMaxObjectSize();
		maxIncomingDataMemoryUsage = cfg.getMaxIncomingDataMemoryUsage();
		maxRelabelAndSortMemoryUsage = cfg.getMaxRelabelAndSortMemoryUsage();
		maxReturnedDataMemoryUsage = cfg.getMaxReturnedDataMemoryUsage();
		maxReturnedDataSize = cfg.getMaxReturnedDataSize();
	}
	
	public ResourceUsageConfigurationBuilder withMaxObjectSize(int maxObjectSize) {
		this.maxObjectSize = maxObjectSize;
		return this;
	}

	public ResourceUsageConfigurationBuilder withMaxIncomingDataMemoryUsage(
			int maxIncomingDataMemoryUsage) {
		this.maxIncomingDataMemoryUsage = maxIncomingDataMemoryUsage;
		return this;
	}

	public ResourceUsageConfigurationBuilder withMaxRelabelAndSortMemoryUsage(
			int maxRelabelAndSortMemoryUsage) {
		this.maxRelabelAndSortMemoryUsage = maxRelabelAndSortMemoryUsage;
		return this;
	}

	public ResourceUsageConfigurationBuilder withMaxReturnedDataMemoryUsage(
			int maxReturnedDataMemoryUsage) {
		this.maxReturnedDataMemoryUsage = maxReturnedDataMemoryUsage;
		return this;
	}

	public ResourceUsageConfigurationBuilder withMaxReturnedDataSize(
			long maxReturnedDataSize) {
		this.maxReturnedDataSize = maxReturnedDataSize;
		return this;
	}

	public ResourceUsageConfiguration build() {
		return new ResourceUsageConfiguration(maxObjectSize, 
				maxIncomingDataMemoryUsage, maxRelabelAndSortMemoryUsage,
				maxReturnedDataMemoryUsage, maxReturnedDataSize);
	}

	public class ResourceUsageConfiguration {
		
		final private int maxObjectSize;
		final private int maxIncomingDataMemoryUsage;
		final private int maxRelabelAndSortMemoryUsage;
		final private int maxReturnedDataMemoryUsage;
		final private long maxReturnedDataSize;

		private ResourceUsageConfiguration(final int maxObjectSize,
				final int maxIncomingDataMemoryUsage,
				final int maxRelabelAndSortMemoryUsage,
				final int maxReturnedDataMemoryUsage,
				final long maxReturnedDataSize) {
			checkGTZero(maxObjectSize, "Maximum object size");
			checkGTZero(maxIncomingDataMemoryUsage, "Maximum incoming data memory usage ");
			checkGTZero(maxRelabelAndSortMemoryUsage, "Relabel and sort memory usage");
			checkGTZero(maxReturnedDataMemoryUsage, "Returned data memory usage");
			checkGTZero(maxReturnedDataSize, "Returned data size");
			
			this.maxObjectSize = maxObjectSize;
			this.maxIncomingDataMemoryUsage = maxIncomingDataMemoryUsage;
			if (maxRelabelAndSortMemoryUsage <= maxIncomingDataMemoryUsage) {
				throw new IllegalArgumentException(
						"Max relabel and sort memory must be greater than the incoming data memory allowance");
			}
			this.maxRelabelAndSortMemoryUsage = maxRelabelAndSortMemoryUsage;
			this.maxReturnedDataMemoryUsage = maxReturnedDataMemoryUsage;
			if (maxReturnedDataSize < maxObjectSize) {
				throw new IllegalArgumentException(
						"Max returned data size must be greater than the max object size");
			}
			this.maxReturnedDataSize = maxReturnedDataSize;
		}

		private void checkGTZero(long maxReturnedDataDiskUsage, String name) {
			if (maxReturnedDataDiskUsage < 1) {
				throw new IllegalArgumentException(name + " must be greater than zero");
			}
		}

		/** The maximum object size allowed. Typed objects greater than this
		 * size will cause an error to be thrown.
		 * @return the maximum allowed object size.
		 */
		public int getMaxObjectSize() {
			return maxObjectSize;
		}

		/** The maximum memory to use for typed objects when saving data per
		 * method call, excluding relabeling and sorting.
		 * This does not account for any
		 * memory use prior to calling the saveObjects method, nor for
		 * anything except for typed object data (e.g. provenance, metadata,
		 * etc are not included). If the total size of the typed objects in one
		 * saveObjects call is greater than this amount, the data will be saved
		 * to disk for processing rather than kept in memory, which could
		 * significantly slow down operations.
		 * @return the maximum memory allowed for incoming typed objects.
		 */
		public int getMaxIncomingDataMemoryUsage() {
			return maxIncomingDataMemoryUsage;
		}

		/** The maximum approximate memory to use when sorting data.
		 * @return the maximum approximate memory to use when sorting data.
		 */
		public int getMaxRelabelAndSortMemoryUsage() {
			return maxRelabelAndSortMemoryUsage;
		}

		/** The maximum memory to use for typed objects when returning data per
		 * method call.
		 * This does not account for any memory use prior to calling methods
		 * that return typed objects, nor for anything except for typed object
		 * data (e.g. provenance, metadata, etc are not included). Typed
		 * objects will be kept in memory for processing until this limit is
		 * reached, and afterwards will be stored on disk, which could
		 * significantly slow down operations.
		 * @return the maximum memory allowed for outgoing typed objects.
		 */
		public int getMaxReturnedDataMemoryUsage() {
			return maxReturnedDataMemoryUsage;
		}
		
		/** The maximum amount of typed object (and only counting typed
		 * objects - not including provenance, metadata, etc.) to return per
		 * call. The maximum disk usage per call is 2x this amount.
		 * @return the maximum amount of typed object data to return per call.
		 */
		public long getMaxReturnedDataSize() {
			return maxReturnedDataSize;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + maxIncomingDataMemoryUsage;
			result = prime * result + maxObjectSize;
			result = prime * result + maxRelabelAndSortMemoryUsage;
			result = prime * result + maxReturnedDataMemoryUsage;
			result = prime * result + (int) (maxReturnedDataSize ^ (maxReturnedDataSize >>> 32));
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
			ResourceUsageConfiguration other = (ResourceUsageConfiguration) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (maxIncomingDataMemoryUsage != other.maxIncomingDataMemoryUsage) {
				return false;
			}
			if (maxObjectSize != other.maxObjectSize) {
				return false;
			}
			if (maxRelabelAndSortMemoryUsage != other.maxRelabelAndSortMemoryUsage) {
				return false;
			}
			if (maxReturnedDataMemoryUsage != other.maxReturnedDataMemoryUsage) {
				return false;
			}
			if (maxReturnedDataSize != other.maxReturnedDataSize) {
				return false;
			}
			return true;
		}

		private ResourceUsageConfigurationBuilder getOuterType() {
			return ResourceUsageConfigurationBuilder.this;
		}
		
		
	}

}
