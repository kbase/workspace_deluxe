SERVICE_PORT = 7068
SERVICE_NAME = workspace
SERVICE_NAME_CAPS = Workspace

TOP_DIR = ../..
DEPLOY_RUNTIME ?= /kb/runtime
TARGET ?= /kb/deployment

SERVICE_DIR ?= $(TARGET)/services/$(SERVICE_NAME)

ANT = ant

# make sure our make test works
.PHONY : test

default: build-libs build-docs

build-libs:
	@# at some point make dependent on compile - checked in for now.
	$(ANT) compile
	
build-docs:
	-rm -r docs 
	$(ANT) javadoc

compile: compile-typespec compile-java

compile-java:
	gen_java_types -S -o . -u http://kbase.us/services/$(SERVICE_NAME)/ $(SERVICE_NAME).spec
	-rm lib/*.jar

compile-typespec:
	mkdir -p lib/biokbase/$(SERVICE_NAME)
	touch lib/biokbase/__init__.py # do not include code in biokbase/__init__.py
	touch lib/biokbase/$(SERVICE_NAME)/__init__.py 
	mkdir -p lib/javascript/$(SERVICE_NAME)
	compile_typespec \
		--client Bio::KBase::$(SERVICE_NAME)::Client \
		--py biokbase/$(SERVICE_NAME)/client \
		--js javascript/$(SERVICE_NAME)/Client \
		--url http://kbase.us/services/$(SERVICE_NAME)/ \
		$(SERVICE_NAME).spec lib
	-rm lib/$(SERVICE_NAME_CAPS)Server.pm
	-rm lib/$(SERVICE_NAME_CAPS)Impl.pm
	-rm lib/$(SERVICE_NAME_CAPS)Server.py
	-rm lib/$(SERVICE_NAME_CAPS)Impl.py


