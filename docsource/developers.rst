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
* ``staging`` - as dev.
* ``master`` - All tests pass, code is production ready.

``develop`` deploys to ``ci.kbase.us`` while ``staging`` deploys to 
``next.kbase.us``. Generally speaking, most development would occur on
``develop``, but because most of ``ci`` would break if the workspace breaks,
``develop`` must be kept stable.

Recompiling the generated code
------------------------------
To compile, simply run ``make compile``. The
`kb-sdk <https://github.com/kbase/kb_sdk>`_ executable must be in the system
path.

Release checklist
-----------------

* Update the version in ``docsource/conf.py``
* Update the version in the generated server java file
* Update release notes
* Update documentation if necessary.
* Ensure tests cover changes. Add new tests if necessary.
* Run tests against supported versions of MongoDB and Shock.
* Tag the release in git with the new version
* Merge ``dev-candidate`` to ``develop``
* When satisfied with CI testing (work with devops here), merge ``develop`` to
  ``staging``
* When satisfied with testing on ``next.kbase.us`` merge ``staging`` to
  ``master``.
