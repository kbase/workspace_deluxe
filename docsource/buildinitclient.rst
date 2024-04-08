.. _buildinitclient:

Build and initialize the workspace client
=========================================

This documentation describes how to build and initialize the workspace clients.
It assumes the build occurs on Ubuntu 18.04LTS. It assumes that the
``workspace_deluxe`` repo has been cloned (see :ref:`getcode`).

Python client
-------------

The Python client checked into ``libs/biokbase/workspace/client.py`` does not
require a build, but does require the `requests <https://pypi.org/project/requests/>`_ library.

Change the working directory to the lib directory::

   bareubuntu@bu:~/ws$ cd workspace_deluxe/lib/
   bareubuntu@bu:~/ws/workspace_deluxe/lib$

Alternatively, add this directory to the ``PYTHONPATH``.

Here we use the iPython interpreter to demonstrate initializing the client,
but the standard python interpreter will also work::

    bareubuntu@bu:~/ws/workspace_deluxe/lib$ ipython

.. code-block:: python

    In [1]: from biokbase.workspace.client import Workspace
    In [2]: ws = Workspace('https://kbase.us/services/ws', token=[redacted])
    In [3]: ws.ver()
    Out[3]: u'0.14.2'

Developer tokens are available from the Account page of the KBase Narrative for approved developers
or can be created in the testmode API of the
`KBase authentication server <https://github.com/kbase/auth2/>`_ if running a local workspace and
auth server.

Java client
-----------

The Java client build requires Java JDK 11+.

Build the client::

    bareubuntu@bu:~/ws/workspace_deluxe$ ./gradlew jar

The client jar is created in ``build/libs/workspace_deluxe-client.jar``.

For simplicity, copy the required jars into a single directory. You will also need the following
jars, which can be downloaded from a maven repository or https://jitpack.io:

* The `Jackson <https://github.com/FasterXML/jackson/>`_ annotations, core, and databind jars
  (maven)
* The javax annotation api jar (maven)
* The `KBase auth client jar <https://github.com/kbase/auth2_client_java/>`_
* The `KBase java_common jar <https://github.com/kbase/java_common/>`_

::

    bareubuntu@bu:~/ws$ mkdir tryjavaclient
    bareubuntu@bu:~/ws$ cd tryjavaclient/
    bareubuntu@bu:~/ws/tryjavaclient$ ls

    auth2_client_java-0.5.0.jar    java_common-0.3.0.jar
    jackson-annotations-2.9.9.jar  javax.annotation-api-1.3.2.jar
    jackson-core-2.9.9.jar         workspace_deluxe-client.jar
    jackson-databind-2.9.9.jar

When creating an application using the WSS it's advisable to use a build tool
like ``ant``, ``maven``, or ``gradle`` to organize the required jars.

This simple program initializes and calls a method on the WSS client::

    bareubuntu@bu:~/ws/tryjavaclient$ cat TryWorkspaceClient.java

.. code-block:: java

    import java.net.URI;
    import java.net.URL;
    import us.kbase.auth.AuthToken;
    import us.kbase.auth.client.AuthClient;
    import us.kbase.workspace.WorkspaceClient;

    public class TryWorkspaceClient {

        public static void main(String[] args) throws Exception {
            final String authUrl = "https://appdev.kbase.us/services/auth/";
            final AuthClient authcli = AuthClient.from(new URI(authUrl));

            final String tokenString = args[0];
            final AuthToken token = authcli.validateToken(tokenString);

            final WorkspaceClient client = new WorkspaceClient(
                    new URL("https://appdev.kbase.us/services/ws/"),
                    token);
            System.out.println(client.ver());
        }
    }

Compile and run::

    bareubuntu@bu:~/ws/tryjavaclient$ javac -cp "./*" TryWorkspaceClient.java
    bareubuntu@bu:~/ws/tryjavaclient$ java -cp "./:./*" TryWorkspaceClient $KBASE_TOKEN
    0.14.2

For more client initialization and configuration options, see :ref:`apidocs`.

Javascript client
-----------------

.. todo::
   Build (probably not needed) and initialization instructions for the
   Javascript client.
