Known administration bugs
=========================

* Due to application level locks in the type database portion of the server,
  only one instance of the server can be run at once. However, see
  :ref:`workspacescaling` for a workaround. 
  
.. note::
   In the future the type service may be separated from the workspace service,
   which would mean the workspace service could run multiple instances.
   The vast majority of the load is on the workspace service.