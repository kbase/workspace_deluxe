package us.kbase.workspace.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

//http://piotrga.wordpress.com/2009/03/27/hamcrest-regex-matcher/
@SuppressWarnings("rawtypes")
public class RegexMatcher extends BaseMatcher{
	private final String regex;

	public RegexMatcher(String regex){
		this.regex = regex;
	}

	public boolean matches(Object o){
		return ((String)o).matches(regex);

	}

	public void describeTo(Description description){
		description.appendText("matches regex=");
	}

	public static RegexMatcher matches(String regex){
		return new RegexMatcher(regex);
	}
}
