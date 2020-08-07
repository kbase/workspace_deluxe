.. _sampleserviceintegration:

Sample service integration with the workspace service
=====================================================

Overview
--------

The `Sample service <https://github.com/kbase/sample_service>`_ stores information about
biological samples.

This document describes how to link WSS objects to samples such that when the object is
shared via the Workspace API, the linked samples are shared (more specifically, made readable)
as well.
If a data type developer merely stored a sample ID in a workspace object
as a string, sharing the object would not share the underlying sample, and
sharees would not be able to access the sample.

.. warning::
   Samples shared by the workspace are not unshared if the workspace object
   containing the sample is unshared. The samples can always be unshared via the Sample
   service API.

.. warning::
   Sharing workspace objects containing links to samples shares the samples as well. If a
   workspace object is copied into a user's workspace and that workspace is made public, the
   samples are set to publicly readable.
   
.. warning::
   To create a workspace object containing a sample ID, the user must have administrative rights
   to the sample - users cannot create objects containing IDs of other users' samples. This is
   because, effectively, saving an object containing a sample ID grants anyone with read
   permissions to that object partial administrative permissions to the sample.

Resources
---------

:ref:`typedobjects` describes how to create workspace types.

Creating objects linked to samples
----------------------------------

The process of linking workspace objects to one or more samples is very similar to that described
in :ref:`shockintegration`. Prior to saving objects, an appropriate type must exist that
supports sample service IDs. The simplest possible type is::

    /* @id sample */
    typedef string sample_id;
 
    typedef structure {
        sample_id sid;
    } StructWithSampleID;

Assuming an appropriate type exists and that the user has created a sample in the sample
service (or is an administrator of another user's sample), the user saves an object containing
the ID of said sample in the appropriate place in the object structure. For the toy type above,
the object would look like::

    {"sid": [sample ID string goes here]}

Otherwise, the saving process is identical to saving any other object. During the save, the
workspace checks that the user administrates or owns the sample(s), and rejects the
save if such is not the case.

Retrieving the data from the workspace also works normally, but there’s a
couple of important points. When calling the ``get_objects2`` method (or the deprecated
``get_objects``, ``get_referenced_objects``, ``get_object_subset``, or ``get_object_provenance``
methods):

* The sample IDs found in the object are returned in the output as strings in the ``extracted_ids``
  field.
* The Workspace makes a request to the Sample Service such that the caller of
  the method is given read access to the samples referenced by the IDs
  embedded in the object.

This means that, mostly invisibly, the samples embedded in a Workspace object are shared as
the object is shared.

If the sample service is uncontactable or some other error occurs, the workspace will still
return the workspace object. However, the error will be embedded in the returned data structure.
The ``handle_error`` field will contain a brief description of the error, and the
``handle_stacktrace`` field will contain the full stacktrace. If these fields are populated the
ACLs of some or all of the samples embedded in the object could not be updated.

.. note::
   The ``handle_error`` and ``handle_stacktrace`` fields received their names before the Sample
   service integration existed. They should be called ``external_id_error`` and
   ``external_id_stacktrace``, but are left as is to avoid field duplication and backwards
   compatibility issues.
