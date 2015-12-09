Known User-facing Bugs
======================

* When filtering list_objects output, deleted, hidden and early versions of
  objects are filtered *after* the limit is applied. This means that fewer
  objects than the limit may be returned, and in extreme cases where many
  hidden, deleted, or lower version objects are found no objects may be
  returned.