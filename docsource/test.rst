Test
====

In order to run tests:

* MongoDB must be installed, but not necessarily running.
* Shock must be installed, but not necessarily running.

  * A Linux Shock binary is provided in ``shock_builds``.

.. todo::
   Update these instructions for the `Blobstore <https://github.com/kbase/blobstore>`_,
   which has replaced Shock.

* Minio must be installed, but not necessarily running.

  * Minio version must be greater than 2019-05-23T00-29-34Z.

* The Handle Service must be installed, but not necessarily running. See ``test.cfg.example``
  for setup instructions.
  
* The Sample Service must be installed, but not necessarily running. See ``test.cfg.example``
  for setup instructions.

* `ArangoDB <https://arangodb.com/>`_ must be installed but not necessarily running as it is a
  requirement of the Sample Service.


See :ref:`servicedeps` for more information about these test dependencies.

Next, copy the ``test.cfg.example`` file to ``test.cfg`` and fill in appropriately.

Then::

    cd python_dependencies/
    pipenv shell
    cd ..
    ./gradlew test

The ``testQuick`` target is substantially faster but does not run all tests.

.. todo::
   Move to developer documentation vs. server administrator documenation.
