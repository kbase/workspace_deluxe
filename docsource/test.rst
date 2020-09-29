Test
====

In order to run tests:

* MongoDB must be installed, but not necessarily running.
* Shock must be installed, but not necessarily running.

  * A Linux Shock binary is provided in ``shock_builds``.

* Minio must be installed, but not necessarily running.

  * Minio version must be greater than 2019-05-23T00-29-34Z.

* The Handle Service must be installed, but not necessarily running. See ``test.cfg.example``
  for setup instructions.
  
* The KBase Jars repo must be cloned into the parent directory of the workspace repo directory,
  e.g::

    ls 
    jars  workspace_deluxe

See :ref:`servicedeps` for more information about these test dependencies.

Next, copy the ``test.cfg.example`` file to ``test.cfg`` and fill in appropriately.

Then::

    cd handle_service_test/
    pipenv shell
    cd ..
    make test

The tests currently take 20-30 minutes to run on spinning disks, or 8-10 minutes on SSDs.

