package performance;

import java.util.List;

/** Simple class to record performance of a timed measurement.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class PerformanceMeasurement {
	
	private final static double nanoToSec = 1000000000.0;
	
	private final double mean;
	private final double stddev;
	private final String name;
	private final int N;
	
	
	/** Constructor
	 * @param writes - a set of measurements in ns.
	 */
	public PerformanceMeasurement(final List<Long> meas) {
		this.N = meas.size();
		this.mean = mean(meas);
		this.stddev = stddev(mean, meas, false);
		this.name = null;
	}
	
	public PerformanceMeasurement(final List<Long> meas, String name) {
		this.N = meas.size();
		this.mean = mean(meas);
		this.stddev = stddev(mean, meas, false);
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public int getN() {
		return N;
	}
	
	@Override
	public String toString() {
		return "PerformanceMeasurement [mean=" + mean + ", stddev=" + stddev
				+ ", name=" + name + "]";
	}

	public double getAverageInSec() {
		return mean / nanoToSec;
	}
	
	public static double mean(final List<Long> nums) {
		double sum = 0;
		for (Long n: nums) {
			sum += n;
		}
		return sum / nums.size();
	}
	
	public double getStdDevInSec() {
		return stddev / nanoToSec;
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
