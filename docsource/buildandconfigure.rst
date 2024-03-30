.. _buildconfigdeploy:

Build, configure, and deploy
============================

Build the workspace service
---------------------------

Get the code::

    ~$ mkdir kb
    ~$ cd kb
    ~/kb$ git clone https://github.com/kbase/workspace_deluxe

Build::

    ~/kb$ cd workspace_deluxe/
    ~/kb/workspace_deluxe$ ./gradlew buildAll
    *snip*

``buildAll`` will build 3 jars in ``build/libs``:

* A workspace client jar
* A workspace server WAR file
* A workspace shadow jar containing all test code. This is useful for starting a workpace server
  from other processes without needing a docker container, but should **only** be used for testing.

It will also build the ``build\update_workspace_database_schema`` script which is used to
update the workspace schema if it changes from one version to another.

.. _servicedeps:

Service dependencies
--------------------

The WSS requires `MongoDB <https://mongodb.org>`_ 3.6+ to run. The WSS
may optionally use:

* Any AWS S3 compatible storage system as a file storage backend.
* `Shock <https://github.com/kbase/Shock>`_ for linking WSS objects
  to Shock nodes.
* The `Handle Service <https://github.com/kbase/handle_service2>`_
  to mediate linking workspace objects to Shock nodes (see :ref:`shockintegration`).
* The `Sample Service <https://github.com/kbase/sample_service>`_ version 0.1.1 and above
  to allow linking workspace objects to samples (see :ref:`sampleserviceintegration`).
* Apache Kafka as a notification service.

The WSS is tested against:

* The auth2 branch of the KBase fork of Shock version ``0.9.6``
  (``e9f0e1618e265042bf5cb96429995b5e6ec0a06a``)
* MongoDB version ``3.6.23`` with and without WiredTiger
* Minio version ``2019-05-23T00-29-34Z``
* Handle service commit ``aae2f70120e75d2ccccab1b1c01dbb9e8327eee8`` with ``log.py`` commit
  ``b549c557e3c519e0a55eadf7863a93db25cd6806``
* Sample service commit ``b549c557e3c519e0a55eadf7863a93db25cd6806``

Please see the respective service documentation to set up and run the services
required.

.. note:: The WSS is only compatible with versions of Shock that have been patched to work
   with KBase authentication. As of this writing, that is only the version of Shock linked
   above.

.. note::
   The alternative to S3 as a file storage backend is MongoDB GridFS.
   GridFS is simpler to set up, but locks the entire database when writing
   files. Since the workspace can consume very large files, this can cause a
   significant impact on other database operations.

Configuration
-------------

The workspace configuration is contained in the ``deploy.cfg`` file in the repository root (see
:ref:`configurationparameters`). Copy the provided ``deploy.cfg.example`` file to ``deploy.cfg``
to create the file.

.. note::
   See :ref:`configlistener` for configuration parameters for event listeners.

.. warning::
   ``deploy.cfg`` contains several sets of credentials, and thus should be
   protected like any other file containing unencryted passwords or tokens.
   It is especially important to protect the credentials that the WSS uses
   to talk to S3 (``backend-token``) as they can be used to delete
   or corrupt the workspace data. At minimum, only the user that runs the WSS (which
   should **not** be ``root``) should have read access to ``deploy.cfg``.

.. _configurationparameters:

Configuration parameters
^^^^^^^^^^^^^^^^^^^^^^^^

mongodb-host
""""""""""""
**Required**: Yes

**Description**: Host and port of the MongoDB server, eg. localhost:27017

mongodb-database
""""""""""""""""
**Required**: Yes

**Description**: Name of the workspace MongoDB database

mongodb-type-database
"""""""""""""""""""""
**Required**: If not delegating type operations

**Description**: Name of the workspace MongoDB types database. This database name must not be
the same as ``mongodb-database``.

.. warning:: Once any data has been saved by the workspace, changing the type database will
   result in unspecified behavior, including data corruption.

type-delegation-target
""""""""""""""""""""""
**Required**: If delegating type operations

**Description**: URL of the workspace service to which type operations should be delegated. If
this parameter is set ``mongodb-type-database`` is ignored.

.. warning:: Read :ref:`workspacescaling` carefully before delegating types.

.. warning:: Once any data has been saved by the workspace, changing the type database will
   result in unspecified behavior, including data corruption.

mongodb-retrywrites
"""""""""""""""""""

**Required**: No

**Description**: Setting for the
`MongoDB retryWrites <https://www.mongodb.com/docs/manual/core/retryable-writes/>`_
connection parameter. ``true`` is true, anything else is false. Defaults to false.

mongodb-user
""""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Username for an account with readWrite access to the MongoDB
database

mongodb-pwd
"""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Password for an account with readWrite access to the MongoDB
database

auth2-service-url
"""""""""""""""""
**Required**: Yes

**Description**: URL of the KBase authentication service MKII

auth2-ws-admin-read-only-roles
""""""""""""""""""""""""""""""
**Required**: No

**Description**: KBase authentication server custom roles that designate that the user
possessing the role has authority to run administration methods requiring only read access.
If a role is entered in this field, workspace administrator management is delegated to the
KBase authentication server, and administrators specified in the configuration or added to
the workspace database are ignored. Multiple roles may be specified as a comma separated list.

auth2-ws-admin-full-roles
"""""""""""""""""""""""""
**Required**: No

**Description**: KBase authentication server custom roles that designate that the user
possessing the role has authority to run all administration methods.
If a role is entered in this field, workspace administrator management is delegated to the
KBase authentication server, and administrators specified in the configuration or added to
the workspace database are ignored. Multiple roles may be specified as a comma separated list.

ignore-handle-service
"""""""""""""""""""""
**Required**: If not using handles

**Description**: Set to anything (``true`` is good) to not use handles. In this
case attempting to save an object with a handle will fail. Delete or leave
blank to use handles (the default).

handle-service-url
""""""""""""""""""
**Required**: If using handles

**Description**: The URL of the Handle Service

handle-service-token
""""""""""""""""""""
**Required**: If using handles

**Description**: Credentials for the account approved to assign/modify shock node ACLs.

ws-admin
""""""""
**Required**: No

**Description**: the user name for a workspace administrator. This name, unlike
names added via the ``administer`` API call, is not permanently stored in the
database and thus the administrator will change if this name is changed and the
server restarted. This administrator cannot be removed by the ``administer``
API call. If either ``auth2-ws-admin-read-only-roles`` or ``auth2-ws-admin-full-roles``
contain text, this parameter is ignored and workspace administrator management is
delegated to the KBase authentication server.

backend-type
""""""""""""
**Required**: Yes

**Description**: Determines which backend will be used to store the workspace object data.
Either ``GridFS`` or ``S3``. Note all data other than the object data is stored
in MongoDB.

.. warning:: Once any data has been saved by the workspace, changing the backend type will
   result in unspecified behavior, including data corruption.

backend-url
"""""""""""
**Required**: If using S3 as the file backend.

**Description**: The root url of the S3 server.

.. warning:: Once any data has been saved by the workspace, changing the S3 server
   instance will result in unspecified behavior, including data corruption.

backend-user
""""""""""""
**Required**: If using S3 as the file backend.

**Description**: For S3, the access key for the S3 account that will own the workspace data.

.. warning:: Once any data has been saved by the workspace, changing the backend user will
   result in unspecified behavior, including data corruption.

backend-token
"""""""""""""
**Required**: If using S3 as the file backend.

**Description**: For S3, the access secret for the S3 account that will own the workspace data.

backend-container
"""""""""""""""""
**Required**: If using S3 as the file backend.

**Description**: The name of the S3 bucket in which data will be stored.

backend-region
""""""""""""""
**Required**: If using S3 as the file backend.

**Description**: The S3 region the server will communicate with, e.g. ``us-west-1``.

backend-trust-all-ssl-certificates
""""""""""""""""""""""""""""""""""
**Required**: No

**Description**: Set to ``true`` to trust all SSL certificates, including self-signed certificates,
presented by an S3 backend. Other backend types are unaffected. Any other value handles
certificates normally, which is the default behavior.

.. warning:: Setting this parameter to ``true`` exposes the workspace to Man-In-The-Middle attacks.

bytestream-url
""""""""""""""
**Required**: If linking WSS objects to Shock nodes is desired (See :ref:`shockintegration`).

**Description**: The root url of the Shock server. This may be different from ``backend-url`` if
Shock is also used as the file backend.

.. warning:: Once any data containing Shock node IDs has been saved by the workspace,
   changing the shock server instance will result in unspecified behavior, including data
   corruption.

bytestream-user
"""""""""""""""
**Required**: If linking WSS objects to Shock nodes is desired.

**Description**: The KBase user account that will be used to interact with Shock for the purposes
of linking WSS objects to Shock nodes. This is provided in the configuration as a safety feature,
as the shock token may change, but the user should not. The user associated with the shock token
is checked against ``bytestream-user``, and if the names differ, the server will not start.

.. warning:: Once any data containing Shock node IDs has been saved by the workspace, changing the
   shock user will result in unspecified behavior, including data corruption.

bytestream-token
""""""""""""""""
**Required**: If linking WSS objects to Shock nodes is desired.

**Description**: Token for the shock user account used by the WSS to communicate with Shock.

sample-service-url
""""""""""""""""""
**Required**: If linking WSS objects to samples is desired (See :ref:`sampleserviceintegration`).

**Description**: The root url of the Sample server.

.. warning:: Once any data containing sample IDs has been saved by the workspace,
   changing the sample server instance will result in unspecified behavior, including data
   corruption.

sample-service-admin-token
""""""""""""""""""""""""""
**Required**: If linking WSS objects to samples is desired.

**Description**: Token for the user account used by the WSS to communicate with the Sample
service. Must have full administration permissions for the service.

port
""""
**Required**: Yes

**Description**: The port on which the service will listen

server-threads
""""""""""""""
**Required**: Yes

**Description**: See :ref:`serverthreads`

min-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

max-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

temp-dir
""""""""
**Required**: Yes

**Description**: See :ref:`tempdir`

dont-trust-x-ip-headers
"""""""""""""""""""""""
**Required**: No

**Description**: When ``true``, the server ignores the ``X-Forwarded-For`` and
``X-Real-IP`` headers. Otherwise (the default behavior), the logged IP address
for a request, in order of precedence, is 1) the first address in
``X-Forwarded-For``, 2) ``X-Real-IP``, and 3) the address of the client.

.. _configurationscript:

Deploy and start the server
---------------------------

.. todo::
   This section needs an entire rewrite from scratch with Tomcat as the application server and
   a clean, easy install instruction set or a script to set things up correctly (e.g.
   the port and memory settings in the ``deploy.cfg`` file are currently ignored).
   Currently, the easiest way to run the service locally is via ``docker compose up -d --build``
   which will start a KBase auth server in testmode and the workspace. If deploying outside
   a docker container is required, the best option for now is to inspect the Dockerfile and
   attempt to follow the steps there.

   Also, the developer and administrator server startup documentation should be unified.

To avoid various issues when deploying, ``chown`` the deployment directory
to the user. Alternatively, chown ``/kb/`` to the user, or deploy as root.
::

    ~/kb/workspace_deluxe$ sudo mkdir /kb/deployment
    ~/kb/workspace_deluxe$ sudo chown ubuntu /kb/deployment
    ~/kb/workspace_deluxe$ make deploy
    *snip*
    Makefile:53: Warning! Running outside the dev_container - scripts will not be deployed or tested.

Since the service was deployed outside of the ``dev_container``, the service
needs to be told where ``deploy.cfg`` is located. When built in the
``dev_container``, the contents of ``deploy.cfg`` are automatically copied to
a global configuration and this step is not necessary.
::

    ~/kb/workspace_deluxe$ export KB_DEPLOYMENT_CONFIG=~/kb/workspace_deluxe/deploy.cfg

Next, start the service. If using Shock or the Handle services, ensure they are
up and running before starting the WSS.

The workspace service can be run under multiple servlet 3.1 compliant containers. The
first set of instructions below describe starting/stopping using the Glassfish 3.1.x
servlet container. The Glassfish 3.1.x branch no longer has public support and is scheduled
to be end of lifed entirely in 2019, as a consequence after January 2018, Tomcat 8.5.x
will be the supported servlet engine. The second set of instructions detail how to start
and stop workspace under Tomcat. The directions up to this point for configuration files,
environment variables and dependent services remain the same for both Glassfish and Tomcat.

**Run under Glassfiash 3.1.2**
::

    ~/kb/workspace_deluxe$ /kb/deployment/services/workspace/start_service
    Creating domain Workspace at /kb/deployment/services/workspace/glassfish_domain
    Using default port 4848 for Admin.
    Using default port 8080 for HTTP Instance.
    *snip*
    No domain initializers found, bypassing customization step
    Domain Workspace created.
    Domain Workspace admin port is 4848.
    Domain Workspace allows admin login as user "admin" with no password.
    Command create-domain executed successfully.
    Starting domain Workspace
    Waiting for Workspace to start .......
    Successfully started the domain : Workspace
    domain  Location: /kb/deployment/services/workspace/glassfish_domain/Workspace
    Log File: /kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log
    Admin Port: 4848
    Command start-domain executed successfully.
    Removing options []
    Setting option -Xms10000m
    Removing options ['-Xmx512m']
    Setting option -Xmx15000m
    Restarting Workspace, please wait
    Successfully restarted the domain
    Command restart-domain executed successfully.
    Creating property KB_DEPLOYMENT_CONFIG=/home/ubuntu/kb/workspace_deluxe/deploy.cfg
    Command create-system-properties executed successfully.
    Command create-virtual-server executed successfully.
    Command create-threadpool executed successfully.
    Command create-http-listener executed successfully.
    server.network-config.network-listeners.network-listener.http-listener-7058.thread-pool=thread-pool-7058
    Command set executed successfully.
    server.network-config.protocols.protocol.http-listener-7058.http.timeout-seconds=1800
    Command set executed successfully.
    Application deployed with name app-7058.
    Command deploy executed successfully.
    The server started successfully.

Stop the service::

    ~/kb/workspace_deluxe$ /kb/deployment/services/workspace/stop_service
    Domain Workspace exists at /kb/deployment/services/workspace/glassfish_domain, skipping creation
    Domain Workspace is already running on port 4848
    Command undeploy executed successfully.
    Command delete-http-listener executed successfully.
    Command delete-threadpool executed successfully.
    Command delete-virtual-server executed successfully

Note that the ``stop_service`` script leaves the Glassfish server running.
``kill`` the Glassfish instance to completely shut down the server.

If any problems occur, check the glassfish logs (by default at
``/kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log``
and system logs (on Ubuntu, at ``/var/log/syslog``). If the JVM can't start at
all (for instance, if the JVM can't allocate enough memory), the glassfish
logs are the most likely place to look. If the JVM starts but the workspace
application does not, the system logs should provide answers.

**Run under Tomcat 8.5.x**

As of January 2018, Tomcat 8.5.24 is the production/stable release of Tomcat. The server
can be downloaded from <https://tomcat.apache.org/download-80.cgi>. The workspace service
should be able to run on older and newer versions of Tomcat that support the Servlet 3.1
specification. For production purposes, it is not recommended to run Workspace on versions
of Tomcat that do not support Non-Blocking IO due to potential performance bottlenecks under
high concurrency.

Download Tomcat and unzip into working directory::

    Steves-MBP:workspace_deluxe sychan$ cd tmp
    Steves-MBP:tmp sychan$ wget http://apache.mirrors.ionfish.org/tomcat/tomcat-8/v8.5.24/bin/apache-tomcat-8.5.24.tar.gz
    --2018-01-18 09:40:34--  http://apache.mirrors.ionfish.org/tomcat/tomcat-8/v8.5.24/bin/apache-tomcat-8.5.24.tar.gz
    Resolving apache.mirrors.ionfish.org... 38.126.148.232
    Connecting to apache.mirrors.ionfish.org|38.126.148.232|:80... connected.
    HTTP request sent, awaiting response... 200 OK
    Length: 9487006 (9.0M) [application/x-gzip]
    Saving to: ‘apache-tomcat-8.5.24.tar.gz’

    apache-tomcat-8.5.24.tar.gz                       100%[==========================================================================================================>]   9.05M  1.01MB/s    in 9.1s

    2018-01-18 09:40:47 (1018 KB/s) - ‘apache-tomcat-8.5.24.tar.gz’ saved [9487006/9487006]

    Steves-MBP:tmp sychan$ tar xzf apache-tomcat-8.5.24.tar.gz
    Steves-MBP:tmp sychan$ ls apache-tomcat-8.5.24
    LICENSE		NOTICE		RELEASE-NOTES	RUNNING.txt	bin		conf		lib		logs		temp		webapps		work
    Steves-MBP:tmp sychan$

The next step is to remove the default Tomcat distributed root servlet container and replace it
with the workspace WAR file generated by make, so that the the only code running is the workspace service.

Update Tomcat ROOT warfile::

    Steves-MBP:tmp sychan$ cd apache-tomcat-8.5.24
    Steves-MBP:apache-tomcat-8.5.24 sychan$ ls
    LICENSE		NOTICE		RELEASE-NOTES	RUNNING.txt	bin		conf		lib		logs		temp		webapps		work
    Steves-MBP:apache-tomcat-8.5.24 sychan$ cd webapps/
    Steves-MBP:webapps sychan$ ls
    ROOT		docs		examples	host-manager	manager
    Steves-MBP:webapps sychan$ rm -rf *
    Steves-MBP:webapps sychan$ cp ~/src/workspace_deluxe/dist/WorkspaceService.war ROOT.war
    Steves-MBP:webapps sychan$ ls -l
    total 39704
    -rw-r--r--  1 sychan  staff  20324677 Jan 18 09:50 ROOT.war
    Steves-MBP:webapps sychan$

At this point, we can start Tomcat and it will deploy the WorkspaceService.war file as the
root handler on the default listener port of 8080. However the directives in the
KB_DEPLOYMENT_CONFIG file for *port*, *server-threads*, *min-memory* and *max_memory* are not
implemented in the WARfile code, but in glassfish wrapper scripts. These will need to be
updated manually in the Tomcat configuration files.

*Updating the listener port*

Under the Tomcat root there is a conf/server.xml file, update the following stanza, replacing
the port="8080" assignment with the appropriate port

conf/server.xml::

    <!-- A "Connector" represents an endpoint by which requests are received
            and responses are returned. Documentation at :
            Java HTTP Connector: /docs/config/http.html
            Java AJP  Connector: /docs/config/ajp.html
            APR (HTTP/AJP) Connector: /docs/apr.html
            Define a non-SSL/TLS HTTP/1.1 Connector on port 8080
    -->
    <Connector port="8080" protocol="HTTP/1.1"
                connectionTimeout="20000"
                redirectPort="8443" />

Note that in a environment with high load, the protocol="HTTP/1.1" argument
should be replaced with protocol="org.apache.coyote.http11.Http11Nio2Protocol" to use
the non-blocking IO connector.

*Updating the min/max memory for the JVM*

JVM configurations are handled via environment variables defined a bin/setenv.sh file
that needs to be defined by the developer. Create the following file under the Tomcat
root, and substitute the appropriate values for min_memory and max_memory into the
-Xms and -Xmx flags for JAVA_OPTS. The given values here are reasonable for a test
service on a developer workstation. In production typically 10G is the minimum and
15G is the maximum.

bin/setenv.sh::

    #!/bin/sh
    #
    JAVA_OPTS="-Djava.awt.headless=true -server -Xms1000m -Xmx3000m -XX:+UseG1GC"

*Configure the size of the thread pool*

The thread pool is configured in the conf/server.xml file in the following stanza.

conf/server.xml::

    <!--The connectors can use a shared executor, you can define one or more named thread pools-->
    <!--
    <Executor name="tomcatThreadPool" namePrefix="catalina-exec-"
        maxThreads="20" minSpareThreads="4"/>

The default value is 150 maxThreads. The workspace service is a relatively heavyweight service.
Typically we only use 20 max threads.

Having made any necessary configuration changes, we can start Tomcat using the standard admin
scripts under the bin/ directory. To start Tomcat server in the terminal foreground in order to
observe any server messages, we can use "bin/catalina.sh run". Output very similar to the
following should come up:

Start Tomcat with Workspace service::

    18-Jan-2018 19:55:12.385 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Server built:          Sep 3 2017 17:51:58 UTC
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Server number:         8.5.14.0
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log OS Name:               Linux
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log OS Version:            4.9.49-moby
    18-Jan-2018 19:55:12.386 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Architecture:          amd64
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Java Home:             /usr/lib/jvm/java-8-openjdk-amd64/jre
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log JVM Version:           1.8.0_141-8u141-b15-1~deb9u1-b15
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log JVM Vendor:            Oracle Corporation
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log CATALINA_BASE:         /kb/deployment/services/workspace/tomcat
    18-Jan-2018 19:55:12.387 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log CATALINA_HOME:         /usr/share/tomcat8
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.util.logging.config.file=/kb/deployment/services/workspace/tomcat/conf/logging.properties
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.awt.headless=true
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Xms1000m
    18-Jan-2018 19:55:12.388 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Xmx3000m
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -XX:+UseG1GC
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djdk.tls.ephemeralDHKeySize=2048
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.protocol.handler.pkgs=org.apache.catalina.webresources
    18-Jan-2018 19:55:12.389 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Dcatalina.base=/kb/deployment/services/workspace/tomcat
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Dcatalina.home=/usr/share/tomcat8
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Command line argument: -Djava.io.tmpdir=/kb/deployment/services/workspace/tomcat/temp
    18-Jan-2018 19:55:12.390 INFO [main] org.apache.catalina.core.AprLifecycleListener.lifecycleEvent The APR based Apache Tomcat Native library which allows optimal performance in production environments was not found on the java.library.path: /usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib
    18-Jan-2018 19:55:12.491 INFO [main] org.apache.coyote.AbstractProtocol.init Initializing ProtocolHandler ["http-nio2-8080"]
    18-Jan-2018 19:55:12.498 WARNING [main] org.apache.tomcat.util.net.Nio2Endpoint.bind The NIO2 connector requires an exclusive executor to operate properly on shutdown
    18-Jan-2018 19:55:12.606 INFO [main] org.apache.catalina.startup.Catalina.load Initialization processed in 606 ms
    18-Jan-2018 19:55:12.637 INFO [main] org.apache.catalina.core.StandardService.startInternal Starting service Catalina
    18-Jan-2018 19:55:12.638 INFO [main] org.apache.catalina.core.StandardEngine.startInternal Starting Servlet Engine: Apache Tomcat/8.5.14 (Debian)
    18-Jan-2018 19:55:12.664 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployWAR Deploying web application archive /kb/deployment/services/workspace/tomcat/webapps/ROOT.war
    18-Jan-2018 19:55:14.312 INFO [localhost-startStop-1] org.apache.jasper.servlet.TldScanner.scanJars At least one JAR was scanned for TLDs yet contained no TLDs. Enable debug logging for this logger for a complete list of JARs that were scanned but no TLDs were found in them. Skipping unneeded JARs during scanning can improve startup time and JSP compilation time.
    MongoDB reconnect value is 0
    Warning - the Auth Service MKII url uses insecure http. https is recommended.
    Warning - the Auth Service url uses insecure http. https is recommended.
    Warning - the Handle Service url uses insecure http. https is recommended.
    Starting server using connection parameters:
    mongodb-host=ci-mongo
    mongodb-database=workspace
    mongodb-user=
    auth2-service-url=http://auth:8080/
    auth-service-url=http://auth:8080/api/legacy/KBase
    handle-service-url=http://handle_service:8080/
    Temporary file location: ws_temp_dir
    Initialized Shock backend
    Started workspace server instance 1. Free mem: 936900632 Total mem: 1048576000, Max mem: 3145728000
    18-Jan-2018 19:55:15.574 INFO [localhost-startStop-1] org.apache.catalina.startup.HostConfig.deployWAR Deployment of web application archive /kb/deployment/services/workspace/tomcat/webapps/ROOT.war has finished in 2,910 ms
    18-Jan-2018 19:55:15.586 INFO [main] org.apache.coyote.AbstractProtocol.start Starting ProtocolHandler ["http-nio2-8080"]
    18-Jan-2018 19:55:15.588 INFO [main] org.apache.catalina.startup.Catalina.start Server startup in 2981 ms

The tomcat service can be stopped by entering "ctrl-C" from the terminal where tomcat is
running the foreground. An alternative that has Tomcat running the background
would be to start Tomcat in the background using "catalina.sh start|stop" commands.

Catalina.sh start/stop::

    120:apache-tomcat-8.5.24 sychan$ bin/catalina.sh start
    Using CATALINA_BASE:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_HOME:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_TMPDIR: /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/temp
    Using JRE_HOME:        /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home
    Using CLASSPATH:       /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/bootstrap.jar:/Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/tomcat-juli.jar
    Tomcat started.
    120:apache-tomcat-8.5.24 sychan$ bin/catalina.sh stop
    Using CATALINA_BASE:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_HOME:   /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24
    Using CATALINA_TMPDIR: /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/temp
    Using JRE_HOME:        /Library/Java/JavaVirtualMachines/jdk1.8.0_121.jdk/Contents/Home
    Using CLASSPATH:       /Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/bootstrap.jar:/Users/sychan/src/workspace_deluxe/tmp/apache-tomcat-8.5.24/bin/tomcat-juli.jar
    120:apache-tomcat-8.5.24 sychan$
