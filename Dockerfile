FROM eclipse-temurin:11-jdk as build

WORKDIR /tmp
RUN apt-get update -y && \
        apt-get install -y ant git ca-certificates python3-sphinx && \
        git clone https://github.com/kbase/jars

COPY . /tmp/workspace_deluxe

# set up jars
# RUN git clone https://github.com/kbase/jars && \
# export JARSDIR=$(pwd)/jars/lib/jars/ && \
# # set up handle service
# export HS_COMMIT=08e18379817e16db920501b66ba62b66598f506c && \
# export LOG_COMMIT=b549c557e3c519e0a55eadf7863a93db25cd6806 && \
# git clone https://github.com/kbase/handle_service2.git && \
# cd handle_service2 && \
# git checkout $HS_COMMIT && \
# chmod -R 777 . && \
# cd lib && \
# mkdir biokbase && \
# cd biokbase && \
# wget https://raw.githubusercontent.com/kbase/sdkbase2/$LOG_COMMIT/log.py && \
# cd .. && \
# export HSDIR=$(pwd) && \
# cd ../.. && \
# # set up sample service
# export SAMPLE_COMMIT=6813fb148e95db2b11db6eea04f4d1d45cbb7119 && \
# git clone https://github.com/kbase/sample_service.git && \
# cd sample_service && \
# git checkout $SAMPLE_COMMIT && \
# cd lib && \
# export SAMPLE_DIR=$(pwd)

# # set up arango
# RUN export ARANGODB_VER=3.9.1 && \
# export ARANGODB_V=39 && \
# curl -O https://download.arangodb.com/arangodb$ARANGODB_V/Community/Linux/arangodb3-linux-$ARANGODB_VER.tar.gz && \
# tar -xf arangodb3-linux-$ARANGODB_VER.tar.gz && \
# export ARANGO_EXE=$(pwd)/arangodb3-linux-$ARANGODB_VER/bin/arangod && \
# export ARANGO_JS=$(pwd)/arangodb3-linux-$ARANGODB_VER/usr/share/arangodb3/js/

# # set up blobstore
# RUN wget -q -O blobstore https://github.com/kbase/blobstore/releases/download/v0.1.2/blobstore_linux_amd64 && \
# chmod a+x blobstore && \
# export BLOBEXE=$(pwd)/blobstore

# # set up mongo
# # mongo: 'mongodb-linux-x86_64-3.6.23'
# RUN wget -q http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-3.6.23.tgz && \
# tar xfz mongodb-linux-x86_64-3.6.23.tgz && \
# export MONGOD=$(pwd)/mongodb-linux-x86_64-3.6.23/bin/mongod
# # /usr/local/opt/mongodb-community@4.2/bin

# # set up minio
# # minio: '2019-05-23T00-29-34Z'
# RUN wget -q https://dl.minio.io/server/minio/release/linux-amd64/archive/minio.RELEASE.2019-05-23T00-29-34Z -O minio && \
# chmod a+x minio && \
# export MINIOD=$(pwd)/minio

# RUN
# WORKDIR /tmp
# # set up python dependencies
# RUN cd workspace_deluxe/python_dependencies && \
#         pip install pipenv && \
#         pipenv sync --system

RUN cd workspace_deluxe && \
        make docker_deps

# updated/slimmed down version of what's in kbase/kb_jre
FROM ubuntu:18.04

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

ENV DOCKERIZE_VERSION linux-amd64-v0.6.1
ENV TOMCAT_VERSION tomcat8
USER root

RUN mkdir -p /var/lib/apt/lists/partial && \
    apt-get update -y && \
    apt-get install --no-install-recommends -y ca-certificates ${TOMCAT_VERSION}-user libservlet3.1-java wget && \
    update-ca-certificates && \
    apt-get clean && \
    useradd -c "KBase user" -rd /kb/deployment/ -u 998 -s /bin/bash kbase && \
    mkdir -p /kb/deployment/bin && \
    mkdir -p /kb/deployment/jettybase/logs/ && \
    touch /kb/deployment/jettybase/logs/request.log && \
    chown -R kbase /kb/deployment && \
    cd /kb/deployment/bin && \
    wget -N https://github.com/kbase/dockerize/raw/master/dockerize-${DOCKERIZE_VERSION}.tar.gz && \
    tar xvzf dockerize-${DOCKERIZE_VERSION}.tar.gz && \
    rm dockerize-${DOCKERIZE_VERSION}.tar.gz

COPY --from=build /tmp/workspace_deluxe/deployment/ /kb/deployment/

RUN /usr/bin/${TOMCAT_VERSION}-instance-create /kb/deployment/services/workspace/tomcat && \
    mv /kb/deployment/services/workspace/WorkspaceService.war /kb/deployment/services/workspace/tomcat/webapps/ROOT.war && \
    rm -rf /kb/deployment/services/workspace/tomcat/webapps/ROOT

# Must set catalina_base to match location of tomcat8-instance-create dir
# before calling /usr/share/tomcat8/bin/catalina.sh
ENV CATALINA_BASE /kb/deployment/services/workspace/tomcat
ENV KB_DEPLOYMENT_CONFIG /kb/deployment/conf/deployment.cfg

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/workspace_deluxe.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.0-rc1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="Steve Chan sychan@lbl.gov"

EXPOSE 7058
ENTRYPOINT [ "/kb/deployment/bin/dockerize" ]
WORKDIR /kb/deployment/services/workspace/tomcat
CMD [ "-template", "/kb/deployment/conf/.templates/deployment.cfg.templ:/kb/deployment/conf/deployment.cfg", \
      "-template", "/kb/deployment/conf/.templates/server.xml.templ:/kb/deployment/services/workspace/tomcat/conf/server.xml", \
      "-template", "/kb/deployment/conf/.templates/tomcat-users.xml.templ:/kb/deployment/services/workspace/tomcat/conf/tomcat-users.xml", \
      "-template", "/kb/deployment/conf/.templates/logging.properties.templ:/kb/deployment/services/workspace/tomcat/conf/logging.properties", \
      "-template", "/kb/deployment/conf/.templates/setenv.sh.templ:/kb/deployment/services/workspace/tomcat/bin/setenv.sh", \
      "-stdout", "/kb/deployment/services/workspace/tomcat/logs/catalina.out", \
      "-stdout", "/kb/deployment/services/workspace/tomcat/logs/access.log", \
      "/usr/share/tomcat8/bin/catalina.sh", "run" ]
