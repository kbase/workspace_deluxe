Known administration bugs
=========================

* The WSS occasionally fails to start after a redeploy without restarting
  Glassfish, usually after 25-30 redeploys. Workaround by killing and
  restarting Glassfish.
  
* Due to application level locks in the type database portion of the server,
  only one instance of the server can be run at once. 
  
.. note::
   In the future the type service may be separated from the workspace service,
   which would mean the workspace service could run multiple instances.
   The vast majority of the load is on the workspace service.