package us.kbase.workspace.database;

/** Builder for the set of resource usage parameters for the workspace.
 * @author gaprice@lbl.gov
 *
 */
public class ResourceUsageConfigurationBuilder {
	
	//TODO unit tests
	
	final public static int DEFAULT_MAX_OBJECT_SIZE = 1000000000;
	final public static int DEFAULT_MAX_INCOMING_DATA_MEMORY_USAGE = 100000000;
	final public static int DEFAULT_MAX_RELABEL_AND_SORT_MEMORY_USAGE =
			200000000; // must be at least 1x max data
	final public static int DEFAULT_MAX_RETURNED_DATA_MEMORY_USAGE = 300000000;
	final public static long DEFAULT_MAX_RETURNED_DATA_SIZE = 1000000000L;
	final public static long DEFAULT_MAX_RETURNED_DATA_DISK_USAGE = 
			2 * DEFAULT_MAX_RETURNED_DATA_SIZE; // must be at least 2x max returned data size
	
	private int maxObjectSize;
	private int maxIncomingDataMemoryUsage;
	private int maxRelabelAndSortMemoryUsage;
	private int maxReturnedDataMemoryUsage;
	private long maxReturnedDataSize;
	private long maxReturnedDataDiskUsage;
	
	public ResourceUsageConfigurationBuilder() {
		maxObjectSize = DEFAULT_MAX_OBJECT_SIZE;
		maxIncomingDataMemoryUsage = DEFAULT_MAX_INCOMING_DATA_MEMORY_USAGE;
		maxRelabelAndSortMemoryUsage = DEFAULT_MAX_RELABEL_AND_SORT_MEMORY_USAGE;
		maxReturnedDataMemoryUsage = DEFAULT_MAX_RETURNED_DATA_MEMORY_USAGE;
		maxReturnedDataSize = DEFAULT_MAX_RETURNED_DATA_SIZE;
		maxReturnedDataDiskUsage = DEFAULT_MAX_RETURNED_DATA_DISK_USAGE;
	}
	
	public ResourceUsageConfigurationBuilder(ResourceUsageConfiguration cfg) {
		maxObjectSize = cfg.getMaxObjectSize();
		maxIncomingDataMemoryUsage = cfg.getMaxIncomingDataMemoryUsage();
		maxRelabelAndSortMemoryUsage = cfg.getMaxRelabelAndSortMemoryUsage();
		maxReturnedDataMemoryUsage = cfg.getMaxReturnedDataMemoryUsage();
		maxReturnedDataSize = cfg.getMaxReturnedDataSize();
		maxReturnedDataDiskUsage = cfg.getMaxReturnedDataDiskUsage();
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

	public ResourceUsageConfigurationBuilder withMaxReturnedDataDiskUsage(
			long maxReturnedDataDiskUsage) {
		this.maxReturnedDataDiskUsage = maxReturnedDataDiskUsage;
		return this;
	}
	
	public ResourceUsageConfiguration build() {
		return new ResourceUsageConfiguration(maxObjectSize, 
				maxIncomingDataMemoryUsage, maxRelabelAndSortMemoryUsage,
				maxReturnedDataMemoryUsage, maxReturnedDataSize,
				maxReturnedDataDiskUsage);
	}

	public class ResourceUsageConfiguration {
		
		final private int maxObjectSize;
		final private int maxIncomingDataMemoryUsage;
		final private int maxRelabelAndSortMemoryUsage;
		final private int maxReturnedDataMemoryUsage;
		final private long maxReturnedDataSize;
		final private long maxReturnedDataDiskUsage;

		private ResourceUsageConfiguration(final int maxObjectSize,
				final int maxIncomingDataMemoryUsage,
				final int maxRelabelAndSortMemoryUsage,
				final int maxReturnedDataMemoryUsage,
				final long maxReturnedDataSize,
				final long maxReturnedDataDiskUsage) {
			checkGTZero(maxObjectSize, "Maximum object size");
			checkGTZero(maxIncomingDataMemoryUsage, "Maximum incoming data memory usage ");
			checkGTZero(maxRelabelAndSortMemoryUsage, "Relabel and sort memory usage");
			checkGTZero(maxReturnedDataMemoryUsage, "Returned data memory usage");
			checkGTZero(maxReturnedDataSize, "Returned data size");
			checkGTZero(maxReturnedDataDiskUsage, "Returned data disk usage");
			
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
			if (maxReturnedDataDiskUsage < 2 * maxReturnedDataSize) {
				throw new IllegalArgumentException(
						"Max returned disk usage must be at least 2x greater than the max returned data size");
			}
			this.maxReturnedDataDiskUsage = maxReturnedDataDiskUsage;
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

		public long getMaxReturnedDataSize() {
			return maxReturnedDataSize;
		}

		public long getMaxReturnedDataDiskUsage() {
			return maxReturnedDataDiskUsage;
		}
	}

}
