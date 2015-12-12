Known User-facing Bugs
======================

* When filtering ``list_objects`` output, deleted, hidden and early versions of
  objects are filtered *after* the limit is applied. This means that fewer
  objects than the limit may be returned, and in extreme cases where many
  hidden, deleted, or lower version objects are found no objects may be
  returned.
* The completion time and cpu load of the ``list_objects`` API call is
  proportional to the value of the ``skip`` parameter. Skipping
  hundreds of thousands of objects is strongly discouraged. ``skip`` will be
  removed from the API at some point in the future.