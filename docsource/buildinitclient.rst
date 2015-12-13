Build and Initialize the Workspace Client
=========================================

This documentation describes how to build and initialize the workspace clients.
It assumes the documentation build occurs on Ubuntu 12.04LTS,
but things should work similarly on other distributions. It assumes that the
``workspace_deluxe`` and ``jars`` repos have been cloned (see :ref:`getcode`)
but does **not** assume that the KBase runtime or ``dev_container`` are
installed.

Python client
-------------

Currently the Python client only supports Python 2.7. The Python client checked
into ``libs/biokbase/workspace/client.py`` does not
require a build, but does require the ``requests`` (v 2+) 3rd party library, which,
depending on the Python version, can be 
`tricky to install securely <http://stackoverflow.com/questions/29099404/ssl-insecureplatform-error-when-using-requests-package>`_.
The following incantation worked for the author::

    sudo aptitude install python-dev libffi-dev libssl-dev
    curl https://bootstrap.pypa.io/get-pip.py > get-pip.py
    sudo python get-pip.py
    sudo pip install --upgrade requests
    sudo pip install --upgrade requests[security]
    
For python 2.7.9+ ``sudo pip install --upgrade requests`` should
work.

Change the working directory to the lib directory::

   bareubuntu@bu:~/ws$ cd workspace_deluxe/lib/
   bareubuntu@bu:~/ws/workspace_deluxe/lib$
   
Alternatively, add this directory to the ``PYTHONPATH``. If deploying with
the ``dev_container``, the client will be copied to 
``/kb/deployment/lib/biokbase/workspace/client.py`` and the ``user-env`` script
will set up the ``PYTHONPATH``.

Here we use the iPython interpreter to demonstrate initializing the client,
but the standard python interpreter will also work::

    bareubuntu@bu:~/ws/workspace_deluxe/lib$ ipython
    In [1]: from biokbase.workspace.client import Workspace
    In [2]: ws = Workspace('https://kbase.us/services/ws', user_id='kbasetest', password=[redacted])
    In [3]: ws.ver()
    Out[3]: u'0.3.5'

Java client
-----------

The Java client build requires:

Java JDK 6+ (`install instructions <https://www.digitalocean.com/community/tutorials/how-to-install-java-on-ubuntu-with-apt-get>`_)

`Java ant <http://ant.apache.org/>`_::

    sudo apt-get install ant




.. todo::
   Build and initialization instructions for the Perl client. If this can
   be done without the KBase runtime & dev_container that'd be ideal.
   
.. todo::
   Build (probably not needed) and initialization instructions for the
   Javascript client.

.. todo::
   build & init client instructions
   
   on v26 image, just need jars, java 6, and w_d to make compile-java-client,
   winds up in dist. Check on ubuntu 12.04