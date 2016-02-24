.. _buildinitclient:

Build and initialize the workspace client
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

    sudo apt-get install python-dev libffi-dev libssl-dev
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
    
.. code-block:: python

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

Build the client::

    bareubuntu@bu:~/ws/workspace_deluxe$ make compile-java-client
    ant compile_client
    Buildfile: /home/bareubuntu/ws/workspace_deluxe/build.xml

    compile_client:
        [mkdir] Created dir: /home/bareubuntu/ws/workspace_deluxe/client_classes
        [javac] Compiling 48 source files to /home/bareubuntu/ws/workspace_deluxe/client_classes
          [jar] Building jar: /home/bareubuntu/ws/workspace_deluxe/dist/client/WorkspaceClient.jar
       [delete] Deleting directory /home/bareubuntu/ws/workspace_deluxe/client_classes

    BUILD SUCCESSFUL
    Total time: 3 seconds
    
The client jar is created in ``dist/client/WorkspaceClient.jar``.

For simplicity, copy the required jars into a single directory::

    bareubuntu@bu:~/ws$ mkdir tryjavaclient
    bareubuntu@bu:~/ws$ cd tryjavaclient/
    bareubuntu@bu:~/ws/tryjavaclient$ cp ../workspace_deluxe/dist/client/WorkspaceClient.jar .
    bareubuntu@bu:~/ws/tryjavaclient$ cp ../jars/lib/jars/jackson/jackson-*-2.2.3.jar .
    bareubuntu@bu:~/ws/tryjavaclient$ cp ../jars/lib/jars/kbase/auth/kbase-auth-0.3.1.jar .
    bareubuntu@bu:~/ws/tryjavaclient$ cp ../jars/lib/jars/kbase/common/kbase-common-0.0.10.jar .
    bareubuntu@bu:~/ws/tryjavaclient$ ls
    jackson-annotations-2.2.3.jar  kbase-auth-0.3.1.jar
    jackson-core-2.2.3.jar         kbase-common-0.0.10.jar
    jackson-databind-2.2.3.jar     WorkspaceClient.jar

When creating an application using the WSS it's advisable to use a build tool
like ``ant``, ``maven``, or ``gradle`` to organize the required jars.

This simple program initializes and calls a method on the WSS client::

    bareubuntu@bu:~/ws/tryjavaclient$ cat TryWorkspaceClient.java 

.. code-block:: java

    import java.net.URL;

    import us.kbase.workspace.WorkspaceClient;

    public class TryWorkspaceClient {
	
        public static void main(String[] args) throws Exception {
            WorkspaceClient client = new WorkspaceClient(
                    new URL("https://kbase.us/services/ws"),
                    "kbasetest", [redacted]);
            System.out.println(client.ver());
        }
    }

Compile and run::

    bareubuntu@bu:~/ws/tryjavaclient$ javac -cp "./*" TryWorkspaceClient.java 
    bareubuntu@bu:~/ws/tryjavaclient$ java -cp "./:./*" TryWorkspaceClient
    0.3.5

For more client initialization and configuration options, see :ref:`apidocs`.

Perl client
-----------

.. todo::
   Build and initialization instructions for the Perl client. If this can
   be done without the KBase runtime & dev_container that'd be ideal.

Javascript client
-----------------

.. todo::
   Build (probably not needed) and initialization instructions for the
   Javascript client.
