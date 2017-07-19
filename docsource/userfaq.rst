FAQ
===

I can release a mapping / list / string / etc. type, but I can’t save anything as that type. Why not?
-----------------------------------------------------------------------------------------------------

The workspace is only intended to store typed objects - e.g. a class or
structure in the context of a programming language. This confusion arises
because releasing a type has two conflated meanings. Firstly, releasing a type
means that types in other KIDL specs can import and use the type. Secondly,
releasing a type means that the workspace can save objects as that type - but
the workspace only supports saving KIDL structures.

.. note::
   In the future we may separate these meanings into two separate
   operations, one for releasing a type for use in other types, and one for
   releasing structures for saving to the workspace.

Why do I keep getting type checking errors in Perl when I know I created the right type?
----------------------------------------------------------------------------------------

Perl is weakly typed - e.g. types are coerced as necessary depending on the
context. This means that you may put an integer into a data structure, but it
could be coerced to a string in a subsequent operation. The data is currently
transported to the Workspace Service in JSON, which is strongly typed, as is
the Workspace Service type checker, so if type coercion occurs the type checker
will see (for example) a string when an integer is expected and the data will
fail type checking.

It may help to dump the data object to JSON and inspect it just before sending
it to the workspace and look, for example, for quoted integers (in other words,
strings) where the KIDL type specification requires integers.

Note that ``Data::Dumper`` coerces all data to strings before dumping and is
therefore not useful for debugging type checking problems.