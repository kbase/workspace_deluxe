SERVICE_PORT = 7068
SERVICE = workspace
SERVICE_CAPS = Workspace

TOP_DIR = ../..

#include $(TOP_DIR)/tools/Makefile.common

DEPLOY_RUNTIME ?= /kb/runtime
TARGET ?= /kb/deployment
SERVICE_DIR ?= $(TARGET)/services/$(SERVICE)

ANT = ant

# make sure our make test works
.PHONY : test

default: build-libs build-docs

build-libs:
	@# at some point make dependent on compile - checked in for now.
	$(ANT) compile
	
build-docs: build-libs
	-rm -r docs 
	$(ANT) javadoc
	pod2html --infile=lib/Bio/KBase/$(SERVICE)/Client.pm --outfile=docs/$(SERVICE).html
	rm -f pod2htmd.tmp

compile: compile-typespec compile-java

compile-java:
	gen_java_types -S -o . -u http://kbase.us/services/$(SERVICE)/ $(SERVICE).spec
	-rm lib/*.jar

compile-typespec:
	mkdir -p lib/biokbase/$(SERVICE)
	touch lib/biokbase/__init__.py # do not include code in biokbase/__init__.py
	touch lib/biokbase/$(SERVICE)/__init__.py 
	mkdir -p lib/javascript/$(SERVICE)
	compile_typespec \
		--client Bio::KBase::$(SERVICE)::Client \
		--py biokbase/$(SERVICE)/client \
		--js javascript/$(SERVICE)/Client \
		--url http://kbase.us/services/$(SERVICE)/ \
		$(SERVICE).spec lib
	-rm lib/$(SERVICE_CAPS)Server.p?
	-rm lib/$(SERVICE_CAPS)Impl.p?
	
	
deploy: deploy-client deploy-service

deploy-client: deploy-client-libs deploy-docs deploy-scripts

deploy-client-libs:

deploy-docs:
	mkdir -p $(SERVICE_DIR)/webroot
	cp  -r docs/* $(SERVICE_DIR)/webroot/.

deploy-scripts:
	@echo no scripts to deploy

deploy-service: deploy-service-libs deploy-service-scripts

undeploy:

clean:
	rm -rf docs


