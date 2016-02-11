Known user-facing bugs
======================

* The ``list_objects`` ``skip`` parameter behaves unintuitively and is
  deprecated and will be removed in a future version. The same object may
  appear in ``list_objects`` results even when the ``skip`` parameter setting
  should ensure that each set of returned objects is disjoint with all the
  others. 
* The completion time and cpu load of the ``list_objects`` API call is
  proportional to the value of the ``skip`` parameter. Skipping
  hundreds of thousands of objects is strongly discouraged. ``skip`` will be
  removed from the API at some point in the future.