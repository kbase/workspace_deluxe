Repo for phase 2 workspace service development

COMPILATION REQUIREMENTS:
typecomp dev-prototypes branch
java_type_generator dev branch

For now, all compiled files are checked in.

SETUP

1) A mongodb instance must be up and running.
2) If using Shock as a backend, Shock must be up and running.
3) The Handle Service and Handle Manager must be up and running
4) make
5) if you want to run tests:
5a) MongoDB must be installed, but not necessarily running.
5b) Shock must be installed, but not necessarily running.
5c) MySQL must be installed, but not necessarily running, and AppArmor must be
  configured to allow spawning of a mysql instance with files in non-default
  locations by the user running tests.
5d) The Handle Service must be installed, but not necessarily running.
5e) The Handle Manager must be installed, but not necessarily running.
5f) fill in the the test.cfg config file in ./test
5g) make test
6) fill in deploy.cfg
7) Run administration/initialize.py and follow the instructions
8) make deploy
9) optionally, set KB_DEPLOYMENT_CONFIG appropriately
10) /kb/deployment/services/Workspace/start_service

If the server doesn't start up correctly, check /var/log/syslog and
/kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log 
for debugging information, assuming the deploy is in the default location.