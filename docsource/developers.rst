Developer documentation
=======================

Contributions and branches
--------------------------

All pull requests should go to the ``dev-candidate`` branch or a feature
branch.

Branches:

* ``dev-candidate`` - work in progress goes here, not stable, tests may not
  pass.
* ``develop`` - All tests pass. ``dev-candidate`` is merged here when features
  are ready for release. Ready for integration testing.
* ``master`` - All tests pass, code is production ready.

``develop`` deploys to ``ci.kbase.us``. Generally speaking, most development would occur on
``develop``, but because most of ``ci`` would break if the workspace breaks,
``develop`` must be kept stable.

Recompiling the generated code
------------------------------
To compile, simply run ``make compile``. The
`kb-sdk <https://github.com/kbase/kb_sdk>`_ executable must be in the system
path.

Deploying the Workspace Service locally
----------------------------------------
These instructions are known to work on Ubuntu 16.04 LTS.

1. Install the dependencies `Java8 <http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_, `Apache Ant <https://ant.apache.org/bindownload.cgi>`_, pymongo v2.8, `GlassFish v3.1.2.2 <http://www.oracle.com/technetwork/middleware/glassfish/downloads/ogs-3-1-1-downloads-439803.html>`_ , `mongodb >=v2.6.* <https://www.mongodb.com/download-center#atlas>`_, `kb-sdk <https://github.com/kbase/kb_sdk>`_ and the `KBase Jars <https://github.com/kbase/jars>`_ directory.

.. code-block:: bash

    $ sudo pip install pymongo==2.8

The unix shell script (ogs-3.1.2.2-unix.sh) install of GlassFish is simple since the application configuration is also handled during install. Follow the wizard instructions to complete the GlassFish installation. Leave all config values to default values.

The KBase Jars directory must be placed in a directory that is adjacent to the workspace directory.

The rest of this playbook assumes that you have the Glassfish, Mongo and kb-sdk binaries in your system environment path variable.

.. code-block:: bash

   $ cd [PATH_TO_YOUR_WORKSPACE_DIR]
   $ cd ..
   $ git clone https://github.com/kbase/jars.git


2. Start mongodb. Open a new terminal and create a directory for your mongo data (if one does not already exist), then start mongodb.

.. code-block:: bash

    $ mkdir ~/mongodata
    $ mongod --dbpath ~/mongodata

3. Set up the workspace for deployment in another terminal.

.. code-block:: bash

   $ cd [PATH_TO_YOUR_WORKSPACE_DIR]

.. note::

    If you are using Oracle Java 8, the javadoc command may throw errors and warnings. Add the following linter argument line to the javadoc command in build.xml to suppress these warnings and errors.

.. code-block:: xml

    <javadoc access="protected" author="false" classpathref="compile.classpath"
      destdir="${doc}" nodeprecated="false" nodeprecatedlist="false"
      noindex="false" nonavbar="false" notree="false"
      source="1.7" splitindex="true" use="true" version="true">
      <arg line="-Xdoclint:none"/>   <!-- ADD THIS LINE -->
      <link href="http://download.oracle.com/javase/8/docs/api/"/>
      ....
    </target>

Then run make.

.. code-block:: bash

    $ make

Set up a fake kbase directory with a softlink to glassfish within it.

.. code-block:: bash

    $ cd ../
    $ mkdir fakekb
    $ cd fakekb
    $ ln -s ~/glassfish3
    $ gedit glassfish3/glassfish/config/osgi.properties

Add this fix at the end of the osgi.properties file -

.. code-block:: cfg

    # fix for java 8
    jre-1.8=${jre-1.7}

Make sure to get latest version of dev-candidate branch from git.

.. code-block:: bash

    $ cd ../workspace_deluxe
    $ git checkout dev-candidate
    $ git pull

Configure the service for deployment. The instructions here assume the deployment is tied to the CI environment.

.. code-block:: bash

    $ cp deploy.cfg.example deploy.cfg
    $ gedit deploy.cfg

Make the following changes -

.. code-block:: cfg

    auth-service-url = https://ci.kbase.us/services/auth/api/legacy/KBase/Sessions/Login
    globus-url = https://ci.kbase.us/services/auth/api/legacy/globus/
    ws-admin = [YOUR_NAME]
    # Note: ignore-handle-service does not exist and needs to be added
    ignore-handle-service = true

4. Initialize and start the workspace service. This deployment uses gridFS rather than shock as a file backend and does not support handles to shock nodes in objects, and any attempt to save an object with handles will fail.

.. code-block:: bash

    $ export KB_DEPLOYMENT_CONFIG=[ABSOLUTE_PATH_TO_deploy.cfg]
    $ make deploy TARGET=[ABSOLUTE_PATH_TO_fakekb_DIR] DEPLOY_RUNTIME=[ABSOLUTE_PATH_TO_fakekb_DIR]
    $ cd administration
    $ python ./initialize.py
    Keep this configuration? yes
    Does mongodb require authentication? no
    Please enter the name of your mongodb type database: ws_types
    Choose a backend: g
    $ cd ..
    $ [PATH_TO_FAKE_KB]/services/workspace/start_service

.. note::

    If workspace service does not start successfully, tail /var/log/syslog for errors.

5. Check if the workspace service is working properly by creating a workspace service client, verifying workspace service version and creating a new workspace.

.. code-block:: bash

    $ cd [PATH_TO_YOUR_WORKSPACE_DIR]/lib
    $ ipython

    In [1]: from biokbase.workspace.client import Workspace
    In [2]: my_ci_token = 'YOUR CI TOKEN'
    In [4]: ws = Workspace("http://localhost:7058", token=my_ci_token)
    In [5]: ws.ver()
    Out[5]: u'0.8.0-dev4'
    In [6]: ws.create_workspace({'workspace': 'myws'})
    Out[7]:
    [1,
    u'myws',
    ...
    ]


Release checklist
-----------------

* Update the version in ``docsource/conf.py``
* Update the version in the generated server java file
* Update release notes
* Update documentation if necessary.
* Ensure tests cover changes. Add new tests if necessary.
* Run tests against supported versions of MongoDB and Shock.
* Tag the release in git with the new version.
* Merge ``dev-candidate`` to ``develop``.
* When satisfied with CI testing (work with devops here), merge ``develop`` to
  ``master``.
