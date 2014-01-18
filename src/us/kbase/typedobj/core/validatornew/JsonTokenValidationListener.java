package us.kbase.typedobj.core.validatornew;

public interface JsonTokenValidationListener {
	public void addError(String message) throws JsonTokenValidationException;
}
