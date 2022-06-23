package us.kbase.typedobj.core;

import static java.util.Objects.requireNonNull;

import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;

/** Provides type definitions to the TypedObjectValidator. */
public interface TypeProvider {

	/** An error thrown when type information could not be fetched from a remote resource. */ 
	public static class TypeFetchException extends Exception {
		private static final long serialVersionUID = 1L;

		/** Create the exception.
		 * @param message the exception message.
		 */
		public TypeFetchException(final String message) {
			super(message);
		}

		/** Create the exception.
		 * @param message the exception message.
		 * @param cause the cause of the exception.
		 */
		public TypeFetchException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}
	
	/** A resolved type and its JSONSchema. */
	public static class ResolvedType {
		private final AbsoluteTypeDefId type;
		private final String jsonSchema;

		/** Construct the resolved type.
		 * @param type the resolved type.
		 * @param jsonSchema the JSONSchema.
		 */
		public ResolvedType(final AbsoluteTypeDefId type, final String jsonSchema) {
			this.type = requireNonNull(type, "type");
			this.jsonSchema = requireNonNull(jsonSchema, "jsonSchema");
		}

		/** Get the resolved type.
		 * @return the type.
		 */
		public AbsoluteTypeDefId getType() {
			return type;
		}

		/** Get the JSONSchema for the type.
		 * @return the JSONSchema.
		 */
		public String getJsonSchema() {
			return jsonSchema;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((jsonSchema == null) ? 0 : jsonSchema.hashCode());
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ResolvedType other = (ResolvedType) obj;
			if (jsonSchema == null) {
				if (other.jsonSchema != null)
					return false;
			} else if (!jsonSchema.equals(other.jsonSchema))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
	}
	
	/** Retrieves the resolved type and JsonSchema type definition document for the
	 * specified type. Unreleased types are not returned unless the type definition ID is
	 * absolute.
	 * @param type a type id.
	 * @return the resolved type with JSONSchema.
	 * @throws TypeStorageException if an error occurs with the type storage
	 * engine.
	 * @throws NoSuchModuleException if the module for the type does not exist.
	 * @throws NoSuchTypeException if the type does not exist.
	 * @throws TypeFetchException if an error occurred while fetching the type.
	 */
	public ResolvedType getTypeJsonSchema(final TypeDefId type)
			throws NoSuchTypeException, NoSuchModuleException,
				TypeStorageException, TypeFetchException;
}
