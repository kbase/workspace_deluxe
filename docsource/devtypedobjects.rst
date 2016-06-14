Developing typed object definitions
===================================

Providing a comprehensive guide for developing type specifications (typespecs)
for typed objects (TOs) in the Workspace Service (WSS) is far beyond the scope
of this documentation, but provided here are some general guidelines and hints.

TO size and composition
-----------------------

* Generally speaking, the approach of translating each row from a traditional
  RDBMS into a single TO is very wrong. The major advantage of TOs is that
  they allow you to compose various related data into a single object.
* It is faster to save and load a single large TO as opposed to a many small
  TOs. Many small objects will also slow the WSS overall and increase the
  WSS index size.

  * The ``get_objects2`` method allows retrieving subsets of a TO from the
    WSS to provide the equivalent of retrieving a few small TOs rather than
    one large TO and then manually extracting the small TOs.
    
* TOs are currently limited to 1GB by the WSS.
* When contemplating TO design, consider how user interfaces might display
  workspaces and objects. Note that workspaces containing thousands of objects
  quickly become untenable.
* Objects which consist mostly of very long strings are usually much less
  useful when stored in the workspace than more structured data objects.
  Objects like this (for example DNA sequence or raw FASTA files) might be
  candidates for storage in `Shock <https://github.com/kbase/shock_service>`_.

Very large objects
------------------

* Although in general, one larger object is better than many smaller objects,
  when objects are in the hundreds of megabytes they become less useful and
  more difficult to deal with.

  * One cannot realistically fetch a very large object (VLO) to a webpage.

* Even when using workspace functions to extract subdata from a VLO, the VLO
  must still be loaded from disk into the workspace service, which could take
  significant time.
* VLOs are slow to transfer in general.
* VLOs take a large amount of memory.
* VLOs can often take 3-20 times the size of the serialized object to represent
  in memory.
* Objects with large numbers of ``mapping`` s or ``structure`` s can use large
  amounts of resources due to repeated keys. Consider using ``tuple`` s instead
  of ``mapping`` s or ``structure`` s.

Annotations
-----------

TO to TO references (@id ws)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* TO to TO references using the ``@id ws`` annotation [see
  :ref:`idannotations`] greatly enhance the utility of typed objects.
* For example, linking a data set TO to the genome TO that the data set
  references enforces and records the relationship in the workspace database.
* If a TO to be saved references a TO that doesn’t exist, the error is caught
  prior to saving the TO in the workspace.
* If you have access to a TO, you can always access the TOs referenced by that
  TO, regardless of the workspace in which they’re stored.
* However, there is a performance cost - each reference must be checked for
  existence in the database. For tens or even hundreds of references this cost
  is not high, but thousands or more unique references will likely slow
  saving of the TO.
  
@optional
^^^^^^^^^

* Avoid the ``@optional`` annotation whenever possible. In some cases its use
  is required, but every ``@optional`` annotation in a typespec makes the 
  associated TOs more difficult to use for downstream programmers.
  If a typespec has no ``@optional`` annotations, a programmer knows exactly
  what data the TO contains and so the code to manipulate it can be simpler and
  therefore less buggy, easier to maintain, and less work to test.

