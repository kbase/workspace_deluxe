package us.kbase.workspace.kbase;

import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ReferenceParser;

public class KBaseReferenceParser implements ReferenceParser {

	@Override
	public ObjectIdentifier parse(final String reference) {
		return IdentifierUtils.processObjectReference(reference);
	}

}
