Repo for phase 2 workspace service development

*WARNING*: DO NOT RUN TESTS ON A PRODUCTION DATABASE! THE TEST SCRIPTS WILL
  WIPE THE DB.
Running tests on a production Shock instance should be safe, but to be
  cautious run the tests on a standalone instance.

COMPILATION REQUIREMENTS:
typecomp dev-prototypes branch
java_type_generator dev branch

MEMORY REQUIREMENTS:
A minimum of 100Mb per thread. Currently the Makefile specifies 20 threads.
Generally speaking, the more memory the better. Multiple people trying to save
multiple large objects may cause OOMs if there is not enough memory for said
objects.

For now, all compiled files are checked in.

SETUP

1) A mongodb instance must be up and running.
2) If using Shock as a backend or running tests, Shock must be up and running.
3) Run administration/initialize.py and follow the instructions
4) make
5) if you want to run tests:
5a) fill in the the test.cfg config file in ./test
5b) make test
6) make deploy
7) fill in deploy.cfg and set KB_DEPLOYMENT_CONFIG appropriately
8) /kb/deployment/services/Workspace/start_service

If the server doesn't start up correctly, check /var/log/syslog and
/kb/runtime/glassfish3/glassfish/domains/domain1/logs/server.log 
for debugging information.