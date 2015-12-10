Developer Documentation
=======================

Contributions and branches
--------------------------

All pull requests should go to the ``dev-candidate`` branch or a feature
branch.

Branches:

* ``dev-candidate`` - work in progress goes here, not stable, tests may not
  pass.
* ``dev`` - All tests pass. dev-candidate is merged here when features are
  ready for release. Ready for integration testing.
* ``staging`` - as dev.
* ``master`` - All tests pass, code is production ready.

.. note::
   ``dev`` will shortly be renamed to ``develop``.
   
``dev`` deploys to ``ci.kbase.us`` while ``staging`` deploys to 
``next.kbase.us``. Generally speaking, most development would occur on ``dev``,
but because most of ``ci`` would break if the workspace breaks, ``dev`` must be
kept stable.

Release checklist
-----------------

* Update the version in ``docsource/conf.py``
* Update the version in the generated server java file
* Update release notes
* Update documentation if necessary.
* Ensure tests cover changes. Add new tests if necessary.
* Tag the release in git with the new version
* Merge ``dev-candidate`` to ``dev``
* When satisfied with CI testing (work with devops here), merge ``dev`` to
  ``staging``
* When satisfied with testing on ``next.kbase.us`` merge ``staging`` to
  ``master``.