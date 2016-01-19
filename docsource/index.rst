.. KBase Workspace documentation master file, created by
   sphinx-quickstart on Tue Dec  8 14:59:23 2015.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

KBase Workspace Service Manual
==============================

Overview
--------

The Workspace Service (WSS) is a language independent remote storage
and retrieval system for KBase typed objects (TO) defined with the KBase
Interface Description Language (KIDL). It has the following primary features:

* Immutable storage of TOs with

  * user defined metadata 
  * data provenance

* Versioning of TOs
* Referencing from TO to TO
* Typechecking of all saved objects against a KIDL specification
* Collecting typed objects into a workspace
* Sharing workspaces with specific KBase users or the world
* Freezing and publishing workspaces
* Serverside extraction of portions of an object

Manual
------

.. toctree::
   :maxdepth: 2
   
   fundamentals
   users
   admins
   developers
   releasenotes
   docTODOs
   
   



