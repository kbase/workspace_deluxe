package us.kbase.workspace.database;

public class DefaultReferenceParser implements ReferenceParser {

	@Override
	public ObjectIdentifier parse(String reference) {
		return ObjectIdentifier.parseObjectReference(reference);
	}
}
