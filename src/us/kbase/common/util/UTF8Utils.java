package us.kbase.common.util;

/** Simple utilities for use with UTF8 byte arrays. Assumes the byte array
 * is valid UTF8.
 * @author gaprice@lbl.gov
 *
 */
public class UTF8Utils {
	
	/** Get the bounds of the UTF8 character at a location. 
	 * @param b the byte array containing valid UTF8 data
	 * @param location the location of the character
	 * @return the bounds of the character at this location in the byte array
	 */
	public static UTF8CharLocation getCharBounds(
			final byte[] b, final int location) {
		
		if (location >= b.length || location < 0) {
			throw new ArrayIndexOutOfBoundsException(
					"location is not within the bounds of b");
		}
		
		if ((b[location] & 0x80) == 0) {
			//first byte is 0, so a 1 byte UTF8 char
			return new UTF8CharLocation(location, 1);
		}
		int l = location;
		while ((b[l] & 0xC0) == 0x80) {
			//first two bytes are 10, so a continuation character
			l--;
			if (l < 0) {
				throw new ArrayIndexOutOfBoundsException(String.format(
						"The start position of the character at location %s is prior to the start of the array",
						location));
			}
		}
		
		if ((b[l] & 0xE0) == 0xC0) {
			//first 3 bytes are 110, so a 2 byte char
			return new UTF8CharLocation(l, 2);
		}
		if ((b[l] & 0xF0) == 0xE0) {
			//first 4 bytes are 1110, so a 3 byte char
			return new UTF8CharLocation(l, 3);
		}
		//no 5+ byte chars
		return new UTF8CharLocation(l, 4);
	}
	
	
	/** Represents the position of a UTF8 character in a byte[]. The
	 * character's bytes may extend past the end of the array, in which case
	 * getLast() may return an out-of-bounds index.
	 * 
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class UTF8CharLocation {
		
		private final int start;
		private final int length; //short? *shrug*
		
		/** Constructor.
		 * @param start the index of the first byte of the character.
		 * @param length the length of the character in bytes (a value from
		 * 1-4).
		 */
		public UTF8CharLocation(int start, int length) {
			if (start < 0) {
				throw new IllegalArgumentException("start must be >= 0");
			}
			if (length < 1 || length > 4) {
				throw new IllegalArgumentException(
						"length must be between 1 and 4");
				// 5 and 6 byte UTF8 chars no longer allowed
			}
			
			this.start = start;
			this.length = length;
		}

		/** Get the index of the first byte of the character.
		 * @return the index of the first byte of the character.
		 */
		public int getStart() {
			return start;
		}

		public int getLength() {
			return length;
		}
		
		/** Get the index of the last byte of the character. This may be
		 * beyond the end of the array.
		 * @return the index of the last byte of the character.
		 */
		public int getLast() {
			return start + length - 1;
		}

		@Override
		public String toString() {
			return "UTF8CharLocation [start=" + start + ", length=" + length
					+ "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + length;
			result = prime * result + start;
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
			UTF8CharLocation other = (UTF8CharLocation) obj;
			if (length != other.length)
				return false;
			if (start != other.start)
				return false;
			return true;
		}
		
	}

}
