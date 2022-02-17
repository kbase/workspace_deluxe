package us.kbase.workspace;

import java.util.UUID;

public class DeleteMeIDHolder {
		public static ThreadLocal<String> CALL_ID = new ThreadLocal<>();
		
		public static void init() {
			CALL_ID.set(UUID.randomUUID().toString());
		}
		
		public static void print(final String message) {
			System.out.println(String.format(
					"%s; %s; %s",
					message,
					CALL_ID.get(),
					System.currentTimeMillis()));
		}
}
