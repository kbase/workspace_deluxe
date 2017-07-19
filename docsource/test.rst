Test
====

Prior to running workspace tests, the workspace source files must be built.
See :ref:`buildconfigdeploy`.

In order to run tests:

* MongoDB must be installed, but not necessarily running.
* Shock must be installed, but not necessarily running.
* `MySQL <https://www.mysql.com/>`_ must be installed, but not necessarily
  running.

  * `AppArmor <http://wiki.apparmor.net>`_ must be configured to allow spawning
    of a mysql instance with files in non-default locations by the user running
    tests.
    
* The Handle Service must be installed, but not necessarily running.
* The Handle Manager must be installed, but not necessarily running.

See :ref:`servicedeps` for more information about these test dependencies.

Next, copy the ``test.cfg.example`` file to ``test.cfg`` and fill in appropriately.

Finally::

    make test

The tests currently take 20-30 minutes to run.

