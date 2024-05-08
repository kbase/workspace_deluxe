Build documentation
===================

This documentation assumes the documentation build occurs on Ubuntu 18.04LTS,
but things should work similarly on other distributions.

Requirements
------------

The build requires:

Java JDK 11

`Python <https://www.python.org/>`_ `Sphinx <https://www.sphinx-doc.org/>`_ 1.3+


.. _getcode:

Getting the code
----------------

Clone the workspace_deluxe repo::

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/workspace_deluxe

Build
-----

Build the documentation::

    bareubuntu@bu:~/ws$ cd workspace_deluxe/
    bareubuntu@bu:~/ws/workspace_deluxe$ ./gradlew buildDocs

The build directory is ``service/build/docs``.
