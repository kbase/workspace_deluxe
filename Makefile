#port is now set in deploy.cfg
SERVICE = workspace
CLIENT_JAR = WorkspaceClient.jar
WAR = WorkspaceService.war
URL = https://kbase.us/services/ws/
GRADLE = ./gradlew

# make sure our make test works
.PHONY : test

default: compile

sdk_docs:
	-rm -r docs
	sphinx-build docsource/ docs
	cp $(SERVICE).spec docs/.
	cp docshtml/* docs/.
	pod2html --infile=lib/Bio/KBase/$(SERVICE)/Client.pm --outfile=docs/$(SERVICE)_perl.html
	rm -f pod2htm?.tmp

docs: sdk_docs
	$(GRADLE) javadoc
	cp -r build/docs/javadoc docs/

compile: compile-typespec compile-typespec-java compile-html

compile-html:
	kb-sdk compile --html --out docshtml $(SERVICE).spec

compile-typespec-java:
	kb-sdk compile  --java --javasrc src --javasrv --out . \
		--url $(URL) $(SERVICE).spec

compile-typespec:
	kb-sdk compile \
		--out lib \
		--jsclname javascript/$(SERVICE)/Client \
		--plclname Bio::KBase::$(SERVICE)::Client \
		--pyclname biokbase.$(SERVICE).client \
		--url $(URL) \
		$(SERVICE).spec
	rm lib/biokbase/workspace/authclient.py

test: test-service

test-service:
	$(GRADLE) test

test-quick:
	$(GRADLE) testNoLongTests

clean:
	$(GRADLE) clean
	-rm -rf docs
	-rm -rf bin
	-rm -rf deployment/services/workspace/*
	@#TODO remove lib once files are generated on the fly
