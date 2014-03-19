package performance;

import java.util.List;

/** Simple class to record performance of a set of reads and writes.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ReadWritePerformance {
	
	
	private final PerformanceMeasurement write;
	private final PerformanceMeasurement read;
	
	/** Constructor
	 * @param writes - a set of writes in ns.
	 * @param reads - a set of reads in ns.
	 */
	public ReadWritePerformance(final List<Long> writes,
			final List<Long> reads) {
		write = new PerformanceMeasurement(writes);
		read = new PerformanceMeasurement(reads);
	}
	
	@Override
	public String toString() {
		return "ReadWritePerformance [write=" + write + ", read=" + read + "]";
	}

	public double getAverageWritesInSec() {
		return write.getAverageInSec();
	}
	
	public double getAverageReadsInSec() {
		return read.getAverageInSec();
	}

	public double getStdDevWritesInSec() {
		return write.getStdDevInSec();
	}
	
	public double getStdDevReadsInSec() {
		return read.getStdDevInSec();
	}
}
