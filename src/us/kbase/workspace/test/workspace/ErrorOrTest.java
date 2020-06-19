package us.kbase.workspace.test.workspace;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.NoSuchElementException;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.exceptions.ErrorOr;
import us.kbase.workspace.database.exceptions.ErrorType;

// also tests the minimal code in ErrorType
public class ErrorOrTest {
	
	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ErrorOr.class).usingGetClass().verify();
	}
	
	@Test
	public void constructWithError() {
		final ErrorOr<Integer> eeyore = new ErrorOr<>(ErrorType.DELETED_WORKSPACE);
		
		assertThat("incorrect is error", eeyore.isError(), is(true));
		assertThat("incorrect error", eeyore.getError(), is(ErrorType.DELETED_WORKSPACE));
		
		assertThat("incorrect error code", eeyore.getError().getErrorCode(), is(50020));
		assertThat("incorrect error text", eeyore.getError().getError(),
				is("Workspace is deleted"));
	}
		
	@Test
	public void constructWithErrorFail() throws Exception {
		try {
			new ErrorOr<>(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("error"));
		}
	}
	
	@Test
	public void getErrorFail() throws Exception {
		try {
			new ErrorOr<>("foo").getError();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NoSuchElementException("error"));
		}
	}
	
	@Test
	public void constructWithObject() throws Exception {
		final ErrorOr<String> eeyore = new ErrorOr<>("foo");
		
		assertThat("incorrect is error", eeyore.isError(), is(false));
		assertThat("incorrect object", eeyore.getObject(), is("foo"));
	}
	
	@Test
	public void constructWithNullObject() throws Exception {
		final ErrorOr<String> eeyore = new ErrorOr<>((String) null);
		
		assertThat("incorrect is error", eeyore.isError(), is(false));
		assertThat("incorrect object", eeyore.getObject(), nullValue());
	}
	
	@Test
	public void getObjectFail() throws Exception {
		try {
			new ErrorOr<>(ErrorType.ILLEGAL_PARAMETER).getObject();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NoSuchElementException("object"));
		}
		
	}

}
