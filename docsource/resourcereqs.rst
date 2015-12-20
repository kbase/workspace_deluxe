Workspace resource requirements
===============================

Several configuration variables define resources which are assigned to the
WSS.

Configuration variables
-----------------------

==============    ============    ==========================================
Variable          Default         Notes
==============    ============    ==========================================
temp-dir          ws_temp_dir     Change to an appropriate location.
server-threads    20
min-memory        10000           In MB.
max-memory        15000           In MB.
==============    ============    ==========================================

.. _tempdir:

temp-dir
^^^^^^^^
**temp-dir** determines where the workspace writes temporary files. The
workspace is by default configured to need no more than 80GB of space at one
time (see :ref:`disk_usage` below). The faster the drive on which the temp
files directory is located, the faster the workspace will process large TOs.

.. _serverthreads:

server-threads
^^^^^^^^^^^^^^
**server-threads** determines how many threads the server will run, which
determines the maximum number of concurrent serviced connections. If more than
this number of connections occur at the same time, they will be processed in
the order received. **server-threads** dictates how much memory and disk space
is needed for the server as a whole - see :ref:`memory_usage` and
:ref:`disk_usage` below.

.. _minmaxmemory:

min-memory and max-memory
^^^^^^^^^^^^^^^^^^^^^^^^^
**min-memory** and **max-memory** set the minimum and maximum memory the
Glassfish server, as a whole, will use (e.g. they're JVM parameters). It is
assumed no other services run on the Glassfish server.

.. _memory_usage:

Memory usage
------------

The workspace currently uses up to 400MB per call for saving data:

+------+---------------------------------------+------------------------------+
|Amount|Use                                    |If exceeded                   |
+======+=======================================+==============================+
|100MB |Storage of the raw rpc data as bytes.  |RPC call is dumped to disk.   |
+------+---------------------------------------+------------------------------+
|100MB |Storage of sorted, relabeled TOs       |All TO data is dumped to disk.|
+------+---------------------------------------+------------------------------+
|200MB |Memory for sorting & intermediate      |Intermediate data is dumped   |
|      |data per TO (processed serially). See  |to disk or an error is        |
|      |:ref:`sorting_notes`.                  |returned if sorting takes     |
|      |                                       |too much memory.              |
+------+---------------------------------------+------------------------------+

Returning data is simpler - 300MB is allocated for all TO data, and any TO data
exceeding this limit is dumped to disk.

Provenance and user provided metadata are not included in these limits but
are expected to be small.

Thus, to be safe the minimum memory for the server should be set to 500MB per
thread (thus the default 10GB for a 20 thread server).

.. note::
   In the future, we hope to add a thread queue that detects free memory so
   that more threads can run when the memory load is not high (which is
   expected to be the case most of the time).

.. _disk_usage:

Disk usage
----------

Disk usage is currently configured to use up to 3GB per call for saving data.

======    =====================================    ===========================
Amount    Use                                      If exceeded
======    =====================================    ===========================
1GB       Storage of the raw rpc data as bytes.    The server throws an error.
1GB       Storage of sorted, relabeled TOs         The server throws an error.
1GB       Storage of intermediate sort files       The server throws an error.
======    =====================================    ===========================

Returning data is configured to use no more than 2GB. Thus, to be perfectly
safe, 4GB per server thread of temporary disk space should be allocated (thus
80GB for a 20 thread server).
