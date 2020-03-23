Test
====

Prior to running workspace tests, the workspace source files must be built.
See :ref:`buildconfigdeploy`.

In order to run tests:

* MongoDB must be installed, but not necessarily running.
* Shock must be installed, but not necessarily running.

  * A Linux Shock binary is provided in ``shock_builds``.

* Minio must be installed, but not necessarily running.

  * Minio version must be greater than 2019-05-23T00-29-34Z.

* The Handle Service must be installed, but not necessarily running. See ``test.cfg.example``
  for setup instructions.

See :ref:`servicedeps` for more information about these test dependencies.

Next, copy the ``test.cfg.example`` file to ``test.cfg`` and fill in appropriately.

Finally::

    make test

The tests currently take 20-30 minutes to run on spinning disks, or 8-10 minutes on SSDs.

