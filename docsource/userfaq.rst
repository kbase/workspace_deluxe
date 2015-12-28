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

How do I build just the workspace scripts?
------------------------------------------

Note this answer assumes the user is familiar with the KBase ``dev_container``,
runtime, modules etc.

Building the workspace scripts is somewhat more complicated than it needs to be
because the ``DEPENDENCIES`` file in KBase doesn't differentiate between
script/client/server dependencies, but it should be mostly straightforward if
the standard kbase ``dev_container`` environment is set up.

If a build of only the scripts is required, check out ``workspace_deluxe``,
``auth``, ``kbapi_common`` and ``jars``.  Then either delete the
``workspace_deluxe/DEPENDENCIES`` file altogether, or edit it to remove
``shock_service``, ``handle_service`` and ``handle_mngr``.

This will allow you to run bootstrap in the ``dev_container`` folder without
module dependency errors.

So from within dev_container, run::

    ./bootstrap [path to runtime]
    source user-env.sh

``[path to runtime]`` is usually just '/kb/runtime'

If you’re deploying from scratch you will also need to run::

    mkdir /kb/dev_container/bin

Then make 'auth' to build the kbase-login and kbase-logout commands::

    cd /kb/dev_container/modules/auth
    make

Then make the scripts only for workspace deluxe.  If you just run make it will
attempt to compile the server code as well::

    cd /kb/dev_container/modules/workspace_deluxe
    make scriptbin

The ``ws-*`` commands should now work and be available on your path.  For
instance, try ``ws-url`` or ``ws-list``.  The scripts will run out of the
``dev_container/bin`` directory.