package us.kbase.workspace.kbase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Util {
	
	public Util() {};
	
	public String getKIDLpath() throws IOException {
		return new BufferedReader(new InputStreamReader(
				getClass().getClassLoader()
				.getResourceAsStream("kidlinit"))).readLine();
	}

}
