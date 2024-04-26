package us.kbase.test.workspace;

import org.mockito.ArgumentMatcher;

import com.mongodb.client.model.UpdateOptions;

// unfortunately equals() doesn't seem to be implemented correctly for UpdateOptions
public class UpdateOptionsMatcher implements ArgumentMatcher<UpdateOptions> {

	private final UpdateOptions expected;
	
	public UpdateOptionsMatcher(final UpdateOptions expected) {
		this.expected = expected;
	}
	
	private <T> boolean nullEquals(final T arg1, final T arg2) {
		if (arg1 == null) {
			return arg2 == null;
		}
		return arg1.equals(arg2);
	}

	@Override
	public boolean matches(final UpdateOptions ui) {
		return ui.isUpsert() == expected.isUpsert() &&
				ui.getBypassDocumentValidation() == expected.getBypassDocumentValidation() &&
				nullEquals(ui.getArrayFilters(), expected.getArrayFilters()) &&
				nullEquals(ui.getCollation(), expected.getCollation());
	}

	@Override
	public String toString() {
		return "UpdateOptionsMatcher [expected=" + expected + "]";
	}
}
