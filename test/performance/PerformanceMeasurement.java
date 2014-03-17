package performance;

import java.util.List;

/** Simple class to record performance of a set of reads and writes.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class PerformanceMeasurement {
	
	private final static double nanoToSec = 1000000000.0;
	
	private final double wMean;
	private final double wStd;
	private final double rMean;
	private final double rStd;
	
	
	/** Constructor
	 * @param writes - a set of writes in ns.
	 * @param reads - a set of reads in ns.
	 */
	public PerformanceMeasurement(final List<Long> writes,
			final List<Long> reads) {
		this.wMean = mean(writes);
		this.rMean = mean(reads);
		this.wStd = stddev(wMean, writes, false);
		this.rStd = stddev(rMean, reads, false);
	}
	
	@Override
	public String toString() {
		return "PerformanceMeasurement [wMean=" + wMean + ", wStd=" + wStd
				+ ", rMean=" + rMean + ", rStd=" + rStd + "]";
	}

	public double getAverageWritesInSec() {
		return wMean / nanoToSec;
	}
	
	public double getAverageReadsInSec() {
		return rMean / nanoToSec;
	}
	
	public static double mean(final List<Long> nums) {
		double sum = 0;
		for (Long n: nums) {
			sum += n;
		}
		return sum / nums.size();
	}
	
	public double getStdDevWritesInSec() {
		return wStd / nanoToSec;
	}
	
	public double getStdDevReadsInSec() {
		return rStd / nanoToSec;
	}
	
	public static double stddev(final double mean, final List<Long> values,
			final boolean population) {
		if (values.size() < 2) {
			return Double.NaN;
		}
		final double pop = population ? 0 : -1;
		double accum = 0;
		for (Long d: values) {
			accum += Math.pow(new Double(d) - mean, 2);
		}
		return Math.sqrt(accum / (values.size() + pop));
	}
	
}
