Repo for phase 2 workspace service development
==============================================

COMPILATION REQUIREMENTS
-------------------------
https://github.com/msneddon/typecomp  
https://github.com/kbase/java_type_generator dev branch  

For now, all compiled files are checked in.

SETUP
-----

1. make
2. if you want to run tests:
    1. MongoDB must be installed, but not necessarily running.
    2. Shock must be installed, but not necessarily running.
    3. MySQL must be installed, but not necessarily running, and AppArmor must be
       configured to allow spawning of a mysql instance with files in non-default
       locations by the user running tests.
    4. The Handle Service must be installed, but not necessarily running.
    5. The Handle Manager must be installed, but not necessarily running.
    6. fill in the the test.cfg config file in ./test
    7. make test
3. A mongodb instance must be up and running.  
4. If using Shock as a backend, Shock must be up and running.  
5. The Handle Service and Handle Manager must be up and running
6. fill in deploy.cfg
7. Run administration/initialize.py and follow the instructions
8. make deploy
9. optionally, set KB_DEPLOYMENT_CONFIG appropriately
10. /kb/deployment/services/Workspace/start_service

If the server doesn't start up correctly, check /var/log/syslog and
/kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log 
for debugging information, assuming the deploy is in the default location.

CONTRIBUTIONS
-------------

All pull requests should go to dev-candidate or a feature branch.

Branches:  
dev-candidate - work in progress goes here, not stable, tests may not pass.  
dev - All tests pass. dev-candidate is merged here when features are ready for release.  
master - All tests pass, code is production ready.  
