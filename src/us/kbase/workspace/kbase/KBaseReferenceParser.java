package us.kbase.workspace.kbase;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ReferenceParser;

public class KBaseReferenceParser implements ReferenceParser {

	//TODO test
	@Override
	public ObjectIdentifier parse(String reference) {
		return KBaseIdentifierFactory.processObjectReference(reference);
	}

}
