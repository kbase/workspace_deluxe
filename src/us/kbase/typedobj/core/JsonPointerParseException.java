package us.kbase.typedobj.core;

import us.kbase.typedobj.exceptions.TypedObjectException;

public class JsonPointerParseException extends TypedObjectException {

    private static final long serialVersionUID = 6004346669723835576L;

    public JsonPointerParseException(String message) {
        super(message);
    }

    public JsonPointerParseException(String message, Throwable e) {
        super(message,e);
    }
}