package us.kbase.typedobj.core;

import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;

/** A type provider for the typed object validator that takes a direct instance
 * of a type database.
 * @author gaprice@lbl.gov
 *
 */
public class LocalTypeProvider implements TypeProvider {

	//TODO unit tests
	
	private final TypeDefinitionDB typeDB;
	
	/** Create a type provider that connects directly to a type database.
	 * @param typeDB a type database.
	 */
	public LocalTypeProvider(final TypeDefinitionDB typeDB) {
		if (typeDB == null) {
			throw new NullPointerException("typeDB cannot be null");
		}
		this.typeDB = typeDB;
	}
	@Override
	public AbsoluteTypeDefId resolveTypeDef(TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException {
		return typeDB.resolveTypeDefId(typeDefId);
	}

	@Override
	public String getTypeJsonSchema(AbsoluteTypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException {
		return typeDB.getJsonSchemaDocument(typeDefId);
	}

}
