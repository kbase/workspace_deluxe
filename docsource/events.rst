Workspace events
================

The workspace sends event notifications for certain actions such as creating a workspace,
saving or copying an object, etc. System administrators can specify event listeners in the
``deploy.cfg`` file (see ``deploy.cfg.example``) for examples. An
`Apache Kafka <https://kafka.apache.org/>`_ based event listener is provided (see below), and
custom listeners can be implemented.

.. _configlistener:

Configure an event listener
---------------------------

Event listeners are configured in the ``deploy.cfg`` file. At minimum, the listener factory
class must be specified and the listener must be added to the list of active listeners.
For example, given a listener factory class of
``us.kbase.workspace.test.listener.NullListenerFactory`` and a name of ``nulllistener``
the configuration would look like::

    listeners=nulllistener
    listener-nulllistener-class=us.kbase.workspace.test.listener.NullListenerFactory

This will cause the listener implemented in the ``NullListenerFactory`` class to be loaded and
initialized at startup, and it will be fed events as they occur. The ``nulllistener`` name has no
meaning other than to group related parameters together and provide a way to specific which
listener is activated. ``listeners`` is a comma separated list of active listeners.
To deactivate the ``NullListenerFactory``, for example, remove ``nulllistener`` from the
list of ``listeners``.

Some listeners may require configuration, like so::

    listener-nulllistener-config-key1=value1
    listener-nulllistener-config-key2=value2

In this case the ``NullListenerFactory`` will be passed a mapping of ``key1 -> value1`` and
``key2 -> value2`` at startup.

.. _customlisteners:

Custom listeners
----------------

To create a custom listener, implement the
``us.kbase.workspace.listener.WorkspaceEventListenerFactory`` interface. The map passed to the
``configure`` method at startup comes from the ``listener-<name>-config`` values in the
``deploy.cfg`` file.


.. note::

    Note that some administration commands, such as saving an object, allow an administrator to
    impersonate another user. In this case, the username passed to the event listener will be that
    of the impersonated user. Additionally, for some commands that do not require an administrator
    to impersonate a user (such as setting user permissions), the user name passed to the event
    listener may be ``null``.

Kafka listener
--------------

Configure the ``Kafka`` listener like so::

    listeners=listener1, ... , Kafka, ... , listenerN
    listener-Kafka-class=us.kbase.workspace.modules.KafkaNotifierFactory
    listener-Kafka-config-topic=<Kafka topic where events will be sent>
    listener-Kafka-config-bootstrap.servers=<the Kafka bootstrap servers>

Again, the ``Kafka`` name in this context serves only to group parameters together and allow for
an activation / deactivation mechanism.

The ``config-topic`` must abide by the Kafka constraints, but in addition may not include ``.`` or
``_`` characters to avoid ambiguity in how Kafka processes those characters.

``bootstrap.servers`` is identical to the Kafka ``bootstrap.servers`` configuration item.

The Kafka event listener messages are JSON objects:


.. code-block:: none

    {
      "user": <the user that triggered the event.
          May be null if the user is an administrator>,
      "wsid": <the workspace id of the workspace involved in the event>,
      "objid": <the object id of the object involved in the event.
          May be null>
      "ver": <the version of the object involved in the event.
          May be null>
      "time": <the time the event took place in epoch milliseconds>
      "evtype": <the event type>
      "objtype": <the type of the object involved in the event.
          May be null>
      "perm": <the permission set for one or more users. May be null>
      "permusers": <the list of users for whom permissions were altered.
          May be empty>
    }

The ``evtype``, ``time``, and ``wsid`` fields are always present, but other fields are present
or not based on the event type:

======================== ============================= =============== ===============
Event                    Event type                    Addl. Fields    Null admin user
======================== ============================= =============== ===============
save object              NEW_VERSION                   (1)             No
copy one version         NEW_VERSION                   (1)             No
copy all versions        COPY_OBJECT                   objid           No
revert object            NEW_VERSION                   (1)             No
rename object            RENAME_OBJECT                 objid           No
set object un/deleted    OBJECT_DELETE_STATE_CHANGE    objid           No
clone workspace          CLONE_WORKSPACE                               No
set permission           SET_PERMISSION                perm, permusers Yes
set global permission    SET_GLOBAL_PERMISSION                         No
set workspace un/deleted WORKSPACE_DELETE_STATE_CHANGE                 Yes
======================== ============================= =============== ===============

#. objid, ver, objtype

Regarding the ``user`` field, see the note under :ref:`customlisteners` above.