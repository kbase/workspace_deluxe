Build documentation
===================

This documentation assumes the documentation build occurs on Ubuntu 12.04LTS,
but things should work similarly on other distributions. It does **not**
assume that the KBase runtime or ``dev_container`` are installed.

Requirements
------------

The build requires:

Java JDK 8+

`Java ant <http://ant.apache.org/>`_::

    sudo apt-get install ant
  
`Python <https://www.python.org/>`_ `Sphinx <http://sphinx-doc.org/>`_ 1.3+::

    curl https://bootstrap.pypa.io/get-pip.py > get-pip.py
    sudo python get-pip.py
    sudo pip install sphinx --upgrade

.. _getcode:

Getting the code
----------------

Clone the jars and workspace_deluxe repos::

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
    
Build
-----
    
Build the documentation::

    bareubuntu@bu:~/ws$ cd workspace_deluxe/
    bareubuntu@bu:~/ws/workspace_deluxe$ make build-docs
    
The build directory is ``docs``.
