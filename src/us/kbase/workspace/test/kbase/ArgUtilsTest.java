package us.kbase.workspace.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.workspace.kbase.ArgUtils.chooseInstant;
import static us.kbase.common.test.TestCommon.inst;

import java.time.Instant;

import org.junit.Test;

import us.kbase.common.test.TestCommon;

public class ArgUtilsTest {
	
	// TODO TEST add more ArgUtils unit tests
	
	@Test
	public void chooseInstantSuccess() throws Exception {
		checkChooseInstant(null, null, null);
		
		checkChooseInstant(null, -1000L, inst(-1000));
		checkChooseInstant(null, 0L, inst(0));
		checkChooseInstant(null, 1000L, inst(1000));
		checkChooseInstant(null, 897086L, inst(897086));
		
		checkChooseInstant("2013-04-26T23:52:06-1111", null, inst(1367060586000L));
		checkChooseInstant("2013-04-26T23:52:06-0000", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06-00:00", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06Z", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06.Z", null, inst(1367020326000L));
		checkChooseInstant("2013-04-26T23:52:06.-0500", null, inst(1367038326000L));
		checkChooseInstant("2013-04-26T23:52:06.7Z", null, inst(1367020326700L));
		checkChooseInstant("2013-04-26T23:52:06.78Z", null, inst(1367020326780L));
		checkChooseInstant("2013-04-26T23:52:06.789Z", null, inst(1367020326789L));
		checkChooseInstant("2013-04-26T23:52:06.789-0000", null, inst(1367020326789L));
		checkChooseInstant("2013-04-26T23:52:06.789-0600", null, inst(1367041926789L));
		checkChooseInstant("2013-04-26T23:52:06.789-06:00", null, inst(1367041926789L));
		// this is a weird case. Changing either of the time zone offsets to -7 causes a failure as
		// expected, as does switching the order of the offsets.
		// Presumably this is due to the format string accepting time zone offsets
		// both with or without a colon. Not sure how to fix and not particularly concerned since
		// the time is unambiguous and no one is likely to format time stamps like this.
		checkChooseInstant("2013-04-26T23:52:06.789-08:00-0800", null, inst(1367049126789L));
		// another weird case. Obvs there isn't 31 days in April but the smart resolver
		// automatically switches it to 30. I set the resolver to strict mode but then everything
		// started failing with completely unhelpful error messages. So f this for now.
		checkChooseInstant("2013-04-31T23:52:06-0800", null, inst(1367394726000L));
	}
	
	private void checkChooseInstant(
			final String timestamp,
			final Long epochMillis,
			final Instant expected)
			throws Exception {
		final Instant got = chooseInstant(timestamp, epochMillis, "foo");
		assertThat("incorrect instant, got epoch of " + (got == null ? null : got.toEpochMilli()),
				got, is(expected));
	}

	@Test
	public void chooseInstantFail() throws Exception {
		failChooseInstant("ts", 1L, iae("can't provide both error"));

		failChooseInstant("2O13-00-26T23:52:06-0800", 0, true);

		failChooseInstant("2013--01-26T23:52:06-0800", 5, true);
		failChooseInstant("2013-00-26T23:52:06-0800",
				"Invalid value for MonthOfYear (valid values 1 - 12): 0");
		failChooseInstant("2013-13-26T23:52:06-0800",
				"Invalid value for MonthOfYear (valid values 1 - 12): 13");
		failChooseInstant("2013-O1-26T23:52:06-0800", 5, true);
		
		failChooseInstant("2013-04-00T23:52:06-0800",
				"Invalid value for DayOfMonth (valid values 1 - 28/31): 0");
		failChooseInstant("2013-04-32T23:52:06-0800",
				"Invalid value for DayOfMonth (valid values 1 - 28/31): 32");
		failChooseInstant("2013-01-O1T23:52:06-0800", 8, true);
		
		failChooseInstant("2013-04-01T24:52:06-0800",
				"Invalid value for HourOfDay (valid values 0 - 23): 24");
		failChooseInstant("2013-01-01TO2:52:06-0800", 11, true);
		
		failChooseInstant("2013-04-01T23:60:06-0800",
				"Invalid value for MinuteOfHour (valid values 0 - 59): 60");
		failChooseInstant("2013-01-01T02:O4:06-0800", 14, true);
		
		failChooseInstant("2013-04-01T23:59:60-0800",
				"Invalid value for SecondOfMinute (valid values 0 - 59): 60");
		failChooseInstant("2013-01-01T02:07:O9-0800", 17, true);
		
		failChooseInstant("2013-04-26T23:52:0", 17, true);
		failChooseInstant("2013-04-26T23:52:06", null, iae(
				"Date '2013-04-26T23:52:06' does not have time zone information"));
		failChooseInstant("2013-04-26T23:52Z", 16, true);
		failChooseInstant("2013-04-26T23:52-0800", 16, true);
		failChooseInstant("2013-04-26T23:52:06.7893Z", 23, false);
		failChooseInstant("2013-04-26T23:52:06-", 19, false);
		failChooseInstant("2013-04-26T23:52:06-GMT+1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11:", 19, false);
		failChooseInstant("2013-04-26T23:52:06-111", 19, false);
		failChooseInstant("2013-04-26T23:52:06-11:1", 19, false);
		failChooseInstant("2013-04-26T23:52:06-8000", 19, false);
		failChooseInstant("2013-04-26T23:52:06-0800-08:00", 24, false);
		failChooseInstant("2013-04-26T23:52:06-08:00-0700", 25, false);
		failChooseInstant("2013-04-26T23:52:06-07:00-0800", 25, false);
	}
	
	private void failChooseInstant(final String timestamp, final String errsuffix)
			throws Exception {
		failChooseInstant(timestamp, null, iae(generrPrefix(timestamp) + ": " + errsuffix));
	}
	
	private void failChooseInstant(final String timestamp, final int index, final boolean prior)
			throws Exception {
		failChooseInstant(timestamp, null, iae(generr(timestamp, index, prior)));
	}
	
	private IllegalArgumentException iae(final String message) {
		return new IllegalArgumentException(message);
	}
	
	private String generrPrefix(final String timestamp) {
		return String.format("Unparseable date: Text '%s' could not be parsed", timestamp);
	}
	
	private String generr(final String timestamp, final int index, final boolean prior) {
		final String template;
		if (prior) {
			template = generrPrefix(timestamp) + " at index %s";
					
		} else {
			template = generrPrefix(timestamp) + ", unparsed text found at index %s";
		}
		return String.format(template, index);
	}
	
	private void failChooseInstant(
			final String timestamp,
			final Long epochMillis,
			final Exception expected)
			throws Exception {
		try {
			chooseInstant(timestamp, epochMillis, "can't provide both error");
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}

}
