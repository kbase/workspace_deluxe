package us.kbase.common.test.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import us.kbase.common.util.UTF8Utils;
import us.kbase.common.util.UTF8Utils.UTF8CharLocation;

public class TestUTF8Utils {
	
	@Test
	public void utf8CharLocation() throws Exception {
		UTF8CharLocation ucl = new UTF8CharLocation(5, 3);
		assertThat("correct start", ucl.getStart(), is(5));
		assertThat("correct end", ucl.getLast(), is(7));
		assertThat("correct length", ucl.getLength(), is(3));
		
		ucl = new UTF8CharLocation(0, 4);
		assertThat("correct start", ucl.getStart(), is(0));
		assertThat("correct end", ucl.getLast(), is(3));
		assertThat("correct length", ucl.getLength(), is(4));
		
		ucl = new UTF8CharLocation(24, 1);
		assertThat("correct start", ucl.getStart(), is(24));
		assertThat("correct end", ucl.getLast(), is(24));
		assertThat("correct length", ucl.getLength(), is(1));
		
		failCreateCharLoc(1, 5, "length must be between 1 and 4");
		failCreateCharLoc(1, 0, "length must be between 1 and 4");
		failCreateCharLoc(-1, 0, "start must be >= 0");
	}
	
	private void failCreateCharLoc(int start, int length, String exception) {
		try {
			new UTF8CharLocation(start, length);
			fail("created utf8 char loc when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp,
					new IllegalArgumentException(exception));
		}
	}
	
	private void assertExceptionCorrect(Exception got, Exception expected) {
		assertThat("correct exception", got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("correct exception type", got, is(expected.getClass()));
	}

	@Test
	public void getCharBounds() throws Exception {
		StringBuilder sb = new StringBuilder();
		//23 ttl bytes in UTF-8
		sb.appendCodePoint(0x10310);
		sb.appendCodePoint(0x4A);
		sb.appendCodePoint(0x103B0);
		sb.appendCodePoint(0x120);
		sb.appendCodePoint(0x1D120);
		sb.appendCodePoint(0x0A90);
		sb.appendCodePoint(0x6A);
		sb.appendCodePoint(0x1D120);
		byte[] b = sb.toString().getBytes();
		assertThat("found 1st char", UTF8Utils.getCharBounds(b, 2),
				is(new UTF8CharLocation(0, 4)));
		assertThat("found 2nd char", UTF8Utils.getCharBounds(b, 4),
				is(new UTF8CharLocation(4, 1)));
		assertThat("found 3rd char", UTF8Utils.getCharBounds(b, 5),
				is(new UTF8CharLocation(5, 4)));
		assertThat("found 4th char", UTF8Utils.getCharBounds(b, 10),
				is(new UTF8CharLocation(9, 2)));
		assertThat("found 5th char", UTF8Utils.getCharBounds(b, 14),
				is(new UTF8CharLocation(11, 4)));
		assertThat("found 6th char", UTF8Utils.getCharBounds(b, 16),
				is(new UTF8CharLocation(15, 3)));
		assertThat("found 7th char", UTF8Utils.getCharBounds(b, 18),
				is(new UTF8CharLocation(18, 1)));
		assertThat("found 8th char", UTF8Utils.getCharBounds(b, 20),
				is(new UTF8CharLocation(19, 4)));
		
		b = Arrays.copyOfRange(b, 2, 21);
		assertThat("found truncated char", UTF8Utils.getCharBounds(b, 18),
				is(new UTF8CharLocation(17, 4)));
		
		failGetCharBounds(b, 1,
				"The start position of the character at location 1 is prior to the start of the array");
		failGetCharBounds(b, -1, "location is not within the bounds of b");
		failGetCharBounds(b, 19, "location is not within the bounds of b");
		
	}
	
	private void failGetCharBounds(byte[] b, int pos, String e) {
		try {
			UTF8Utils.getCharBounds(b, pos);
			fail("got char bounds when should fail");
		} catch (Exception exp) {
			assertExceptionCorrect(exp, new ArrayIndexOutOfBoundsException(e));
		}
	}

}
