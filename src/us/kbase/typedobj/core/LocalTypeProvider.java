package us.kbase.typedobj.core;

import static java.util.Objects.requireNonNull;

import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;

/** A type provider for the typed object validator that takes a direct instance
 * of a type database.
 *
 */
public class LocalTypeProvider implements TypeProvider {

	private final TypeDefinitionDB typeDB;
	
	/** Create a type provider that connects directly to a type database.
	 * @param typeDB a type database.
	 */
	public LocalTypeProvider(final TypeDefinitionDB typeDB) {
		this.typeDB = requireNonNull(typeDB, "typeDB");
	}

	@Override
	public ResolvedType getTypeJsonSchema(final TypeDefId type)
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		final AbsoluteTypeDefId rtype = typeDB.resolveTypeDefId(requireNonNull(type, "type"));
		return new ResolvedType(rtype, typeDB.getJsonSchemaDocument(rtype));
	}

}
