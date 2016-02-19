package us.kbase.typedobj.core;

import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;

public interface TypeProvider {

	/** Translates a potentially unresolved type id (e.g. missing a minor
	 * or major version) to an absolute type id.
	 * @param typeDefId a type id that may not be completely specified.
	 * @return a completely specified type id.
	 * @throws TypeStorageException if an error occurs with the type storage
	 * engine
	 * @throws NoSuchModuleException if the module for the type does not exit
	 * @throws NoSuchTypeException if the type does not exist
	 */
	public AbsoluteTypeDefId resolveTypeDef(final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException;
	
	/** Retrieves the JsonSchema type definition document, as a string, for the
	 * specified type.
	 * @param typeDefId a type id.
	 * @return the JsonSchema document for the type.
	 * @throws TypeStorageException if an error occurs with the type storage
	 * engine
	 * @throws NoSuchModuleException if the module for the type does not exit
	 * @throws NoSuchTypeException if the type does not exist
	 */
	public String getTypeJsonSchema(final AbsoluteTypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException;
}
