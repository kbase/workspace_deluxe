Type checking error messages
============================

This document provides explanations of several common type checking errors
that may occur when attempting to save a typed object (TO) to the Workspace
Service (WSS) that doesn't match the specified type.

Assume that the following spec has been released::

    module AModule {

        /* @optional opt */
        typedef structure {
            list<mapping<string, int>> array_of_maps;
            int an_int;
            float a_float;
            string a_string;
            int opt;
        } AType;
    };

The examples below show an example object, the error received, and an explanation of the error.

Missing property
----------------
**JSON**::

   {"array_of_maps": [{"one": 1}, {"two": 2}],
    "a_float": 1.4,
    "a_string": "s"
    }

**WSS error**: ``object has missing required properties (["an_int"]), at /``

**Explanation**: The non-optional field an_int is missing.

Float vs. string
----------------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": 2}],
     "an_int": "1",
     "a_float": "1",
     "a_string": "1"
     }

**WSS error**: ``instance type (string) does not match any allowed primitive``
               ``type (allowed: ["integer","number"]), at /a_float``
               
**Explanation**: The value for a_float must be a number, but was sent as a
string.

Integer vs. string
------------------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": 2}],
     "an_int": "1",
     "a_float": 1,
     "a_string": "1"
     }
     
**WSS error**: ``instance type (string) does not match any allowed primitive``
               ``type (allowed: ["integer"]), at /an_int``
               
**Explanation**: The value for an_int must be an integer, but was sent as a
string.

Integer vs. float
-----------------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": 2}],
     "an_int": 1.4,
     "a_float": 1,
     "a_string": "1"
     }
     
**WSS error**: ``instance type (number) does not match any allowed primitive``
               ``type (allowed: ["integer"]), at /an_int``
               
**Explanation**: The value for an_int must be an integer, but was sent as a
float.

String vs. integer
------------------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": 2}],
     "an_int": 1,
     "a_float": 1.4,
     "a_string": 1
     }
     
**WSS error**: ``instance type (integer) does not match any allowed primitive``
               ``type (allowed: ["string"]), at /a_string``
               
**Explanation**: The value for a_string must be a string, but was sent as an
integer.

Embedded
--------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": "2"}],
     "an_int": 1,
     "a_float": 1.4,
     "a_string": "s"
     }
     
**WSS error**: ``instance type (string) does not match any allowed primitive``
               ``type (allowed: ["integer"]), at /array_of_maps/1/two``
               
**Explanation**: The value of the two field in the subdocument in the second
position of the array_of_maps array must be an integer, but was sent as a
string.

Optional
--------
**JSON**::

    {"array_of_maps": [{"one": 1}, {"two": 2}],
     "an_int": 1,
     "a_float": 1.4,
     "a_string": "s",
     "opt": "1"
     }

**WSS error**: ``instance type (string) does not match any allowed primitive``
               ``type (allowed: ["integer"]), at /opt``
               
**Explanation**: The value of the optional field opt must be an integer, but
was sent as a string. Note that in previous examples no error occurred even
though the optional field was omitted.
