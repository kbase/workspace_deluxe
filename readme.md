KBase Workspace Service
=======================

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

Getting documentation
---------------------

The WSS documentation describes how to install, configure, run, develop, and
use the WSS. The easiest way to read the documentation is to find an already
built instance online:

* [KBase Continuous Integration](https://ci.kbase.us/services/ws/docs/)
* [KBase Next](https://next.kbase.us/services/ws/docs/)
* [KBase Production](https://kbase.us/services/ws/docs/)

The documentation can also be read on Github as
[restructured text (reST) files](https://github.com/kbase/workspace_deluxe/tree/master/docsource).

However, Github does not display tables of contents and cross references don't
work, so navigation isn't particularly friendly. Also, the API documentation
isn't available.

The better but slightly harder alternative is to build the documentation
yourself.

Building documentation
----------------------

This documentation assumes the documentation build occurs on Ubuntu 12.04LTS,
but things should work similarly on other distributions. It does **not**
assume that the KBase runtime or `dev_container` are installed.

The build requires:

Java JDK 8+

[Java ant](http://ant.apache.org):

    sudo apt-get install ant
  
[Python](https://www.python.org) [Sphinx](http://sphinx-doc.org/) 1.3+:

    curl https://bootstrap.pypa.io/get-pip.py > get-pip.py
    sudo python get-pip.py
    sudo pip install sphinx --upgrade

Clone the jars and workspace_deluxe repos:

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/jars
    Cloning into 'jars'...
    remote: Counting objects: 1466, done.
    remote: Total 1466 (delta 0), reused 0 (delta 0), pack-reused 1466
    Receiving objects: 100% (1466/1466), 59.43 MiB | 2.43 MiB/s, done.
    Resolving deltas: 100% (626/626), done.

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/workspace_deluxe
    Cloning into 'workspace_deluxe'...
    remote: Counting objects: 22004, done.
    remote: Compressing objects: 100% (82/82), done.
    remote: Total 22004 (delta 41), reused 0 (delta 0), pack-reused 21921
    Receiving objects: 100% (22004/22004), 21.44 MiB | 2.44 MiB/s, done.
    Resolving deltas: 100% (14000/14000), done.
    
Build the documentation:

    bareubuntu@bu:~/ws$ cd workspace_deluxe/
    bareubuntu@bu:~/ws/workspace_deluxe$ make build-docs
    
The build directory is `docs`.