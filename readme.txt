Repo for phase 2 workspace service development

*WARNING*: DO NOT RUN TESTS ON A PRODUCTION DATABASE! THE TEST SCRIPTS WILL
  WIPE THE DB.
Running tests on a production Shock instance should be safe, but to be
  cautious run the tests on a standalone instance.

COMPILATION REQUIREMENTS:
typecomp dev-prototypes branch
java_type_generator dev branch

For now, all compiled files are checked in.

SETUP

1) A mongodb instance must be up and running.
2) If using a shock backend or running tests, shock must be up and running.
3) Run administration/initialize.py and follow the instructions
4) make
5) make test
	TODO more instructions on how to set up make test
6) make deploy
7) /kb/deployment/services/Workspace/start_service