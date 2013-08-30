SERVICE_PORT = 7068
SERVICE = workspace
SERVICE_CAPS = Workspace
CLIENT_JAR = WorkspaceClient.jar
WAR = WorkspaceService.war

THREADPOOL_SIZE = 20

TOP_DIR =  $(shell python -c "import os.path as p; print p.abspath('../..')")

TOP_DIR_NAME = $(shell basename $(TOP_DIR))

ifeq ($(TOP_DIR_NAME), dev_container)
include $(TOP_DIR)/tools/Makefile.common
endif

DEPLOY_RUNTIME ?= /kb/runtime
TARGET ?= /kb/deployment
SERVICE_DIR ?= $(TARGET)/services/$(SERVICE)

ANT = ant

# make sure our make test works
.PHONY : test

default: build-libs build-docs

build-libs:
	@#TODO at some point make dependent on compile - checked in for now.
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
		--py biokbase.$(SERVICE).client \
		--js javascript/$(SERVICE)/Client \
		--url http://kbase.us/services/$(SERVICE)/ \
		$(SERVICE).spec lib
	-rm lib/$(SERVICE_CAPS)Server.p?
	-rm lib/$(SERVICE_CAPS)Impl.p?

test:
	@#TODO
	
deploy: deploy-client deploy-service

deploy-client: deploy-client-libs deploy-docs deploy-scripts

deploy-client-libs:
	mkdir -p $(TARGET)/lib/
	cp dist/client/$(CLIENT_JAR) $(TARGET)/lib/
	cp -rv lib/* $(TARGET)/lib/

deploy-docs:
	mkdir -p $(SERVICE_DIR)/webroot
	cp  -r docs/* $(SERVICE_DIR)/webroot/.

deploy-scripts:
	@echo no scripts to deploy

deploy-service: deploy-service-libs deploy-service-scripts

deploy-service-libs:
	mkdir -p $(SERVICE_DIR)
	cp dist/$(WAR) $(SERVICE_DIR)
	
deploy-service-scripts:
	cp server_scripts/* $(SERVICE_DIR)
	echo "./glassfish_start_service.sh $(SERVICE_DIR)/$(WAR) $(SERVICE_PORT) $(THREADPOOL_SIZE)" > $(SERVICE_DIR)/start_service
	chmod +x $(SERVICE_DIR)/start_service
	echo "./glassfish_stop_service.sh $(SERVICE_PORT)" > $(SERVICE_DIR)/stop_service
	chmod +x $(SERVICE_DIR)/stop_service

undeploy:
	-rm -rf $(SERVICE_DIR)
	-rm -rfv $(TARGET)/lib/Bio/KBase/$(SERVICE)
	-rm -rfv $(TARGET)/lib/biokbase/$(SERVICE)
	-rm -rfv $(TARGET)/lib/javascript/$(SERVICE) 
	-rm -rfv $(TARGET)/lib/$(CLIENT_JAR)

clean:
	-rm -rf docs
	-rm -rf dist
	@#TODO remove lib once files are generated on the fly
