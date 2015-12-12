Build and Configure
===================

Building the Workspace Service
------------------------------

These instructions assume the reader is familiar with the process of deploying
a KBase module, including the `runtime <https://github.com/kbase/bootstrap>`_
and `dev_container <https://github.com/kbase/dev_container>`_, and has access to
a system with the KBase runtime installed. These instructions are based on the
``kbase-image-v26`` runtime image.

Unlike many modules the WSS can be built and tested outside the
``dev_container``, but the ``dev_container`` is required to build and test the
scripts. These instructions are for deploying the server and so do not
address the scripts. Building outside the ``dev_container`` means the Makefile
uses several default values for deployment - if you wish to use other values
deploy from the ``dev_container`` as usual.

First checkout the ``dev_container``::

    /kb$ sudo git clone https://github.com/kbase/dev_container
    Cloning into 'dev_container'...
    remote: Counting objects: 1097, done.
    remote: Total 1097 (delta 0), reused 0 (delta 0), pack-reused 1097
    Receiving objects: 100% (1097/1097), 138.81 KiB, done.
    Resolving deltas: 100% (661/661), done.

.. note::
   In the v26 image, ``/kb`` is owned by ``root``. As an alternative to
   repetitive ``sudo`` s, ``chown`` ``/kb`` to the user.

Bootstrap and source the user environment file, which sets up Java and Perl
paths which the WSS build needs::

    /kb$ cd dev_container/
    /kb/dev_container$ sudo ./bootstrap /kb/runtime/
    /kb/dev_container$ source user-env.sh
    
Now the WSS may be built. If building inside the ``dev_container`` all the
dependencies from the DEPENDENCIES file are required, but to build outside the
``dev_container``, only the ``jars`` and ``workspace_deluxe`` repos are
necessary::

    ~$ mkdir kb
    ~$ cd kb
    ~/kb$ git clone https://github.com/kbase/workspace_deluxe
    Cloning into 'workspace_deluxe'...
    remote: Counting objects: 21961, done.
    remote: Compressing objects: 100% (40/40), done.
    remote: Total 21961 (delta 20), reused 0 (delta 0), pack-reused 21921
    Receiving objects: 100% (21961/21961), 21.42 MiB | 16.27 MiB/s, done.
    Resolving deltas: 100% (13979/13979), done.

    ~/kb$ git clone https://github.com/kbase/jars
    Cloning into 'jars'...
    remote: Counting objects: 1466, done.
    remote: Total 1466 (delta 0), reused 0 (delta 0), pack-reused 1466
    Receiving objects: 100% (1466/1466), 59.43 MiB | 21.49 MiB/s, done.
    Resolving deltas: 100% (626/626), done.

    ~/kb$ cd workspace_deluxe/
    ~/kb/workspace_deluxe$ make
    *snip*
    
``make`` will build:

* A workspace client jar in ``/dist/client``
* A workspace server jar in ``/dist``
* Documentation in ``/docs``

.. note::
   If the build fails due to a sphinx error, sphinx may require an upgrade to
   >= 1.3::
   
       $ sudo pip install sphinx --upgrade

.. todo::
   startup script example w/ discussion of shock vs. GFS
   
.. todo::
   dependency versions (w/ link to GH commit)
   
.. todo::
   table of deploy.cfg params, including ignore_hanldes &
   dont_trust_ip_forwarding

Deploy and start the server
---------------------------

To avoid various issues when deploying, ``chown`` the deployment directory
to the user. Alternatively, chown ``/kb/`` to the user, or deploy as root.
::

    ~/kb/workspace_deluxe$ sudo mkdir /kb/deployment
    ~/kb/workspace_deluxe$ sudo chown ubuntu /kb/deployment
    ~/kb/workspace_deluxe$ make deploy
    *snip*
    Makefile:53: Warning! Running outside the dev_container - scripts will not be deployed or tested.

.. todo::
   start the server instrcutions, plus logging locations

