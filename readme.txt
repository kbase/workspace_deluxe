Repo for phase 2 workspace service development

*WARNING*: DO NOT RUN TESTS ON A PRODUCTION DATABASE! THE TEST SCRIPTS WILL
  WIPE THE DB.

COMPILATION REQUIREMENTS:
typecomp dev-prototypes branch
java_type_generator dev branch

For now, all compiled files are checked in.

SETUP

1) A mongodb instance must be up and running.
2) If using Shock as a backend, Shock must be up and running.
3) make
4) if you want to run tests:
4a) Shock must be installed (but not running).
4b) fill in the the test.cfg config file in ./test
4c) make test
5) fill in deploy.cfg
6) Run administration/initialize.py and follow the instructions
7) make deploy
8) optionally, set KB_DEPLOYMENT_CONFIG appropriately
9) /kb/deployment/services/Workspace/start_service

If the server doesn't start up correctly, check /var/log/syslog and
/kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log 
for debugging information, assuming the deploy is in the default location.