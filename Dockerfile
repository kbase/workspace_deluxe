# FROM amazoncorretto:8 as build
FROM eclipse-temurin:11-jdk as build
# FROM ibmjava:8-sdk as build
# FROM ibm-semeru-runtimes:open-8-jdk as build
# FROM kbase/sdkbase2 as build

RUN apt-get update -y && \
        apt-get install -y tzdata && apt-get install -y ca-certificates python3-sphinx ant git

WORKDIR /tmp
RUN git clone https://github.com/kbase/jars

COPY . /tmp/workspace_deluxe

# RUN pip install configobj
#     cd /tmp && \
#     git clone https://github.com/kbase/jars
WORKDIR /tmp/workspace_deluxe
RUN cp -r target /tmp/jars/lib/jars/ && \
    make docker_deps

# updated/slimmed down version of what's in kbase/kb_jre

# FROM jetty:jre8

FROM bitnami/minideb
# FROM ubuntu:18.04

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

ENV DOCKERIZE_VERSION linux-amd64-v0.6.1
ENV TOMCAT_VERSION tomcat9
USER root

RUN mkdir -p /var/lib/apt/lists/partial && \
    apt-get update -y && \
#    apt-get install --no-install-recommends -y ca-certificates tomcat8-user libservlet3.1-java wget && \
    install_packages ca-certificates ${TOMCAT_VERSION}-user jetty9 libservlet3.1-java wget && \
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

# Must set catalina_base to match location of tomcat9-instance-create dir
# before calling /usr/share/tomcat9/bin/catalina.sh
ENV CATALINA_BASE /kb/deployment/services/workspace/tomcat
ENV KB_DEPLOYMENT_CONFIG /kb/deployment/conf/deployment.cfg

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/workspace_deluxe.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="KBase Developers"

EXPOSE 7058
ENTRYPOINT [ "/kb/deployment/bin/dockerize" ]
WORKDIR /kb/deployment/services/workspace/tomcat
CMD [ "-template", "/kb/deployment/conf/.templates/deployment.cfg.templ:/kb/deployment/conf/deployment.cfg", \
      "-template", "/kb/deployment/conf/.templates/server.xml.templ:/kb/deployment/services/workspace/tomcat/conf/server.xml", \
      "-template", "/kb/deployment/conf/.templates/tomcat-users.xml.templ:/kb/deployment/services/workspace/tomcat/conf/tomcat-users.xml", \
      "-template", "/kb/deployment/conf/.templates/logging.properties.templ:/kb/deployment/services/workspace/tomcat/conf/logging.properties", \
#       "-template", "/kb/deployment/conf/.templates/setenv.sh.templ:/kb/deployment/services/workspace/tomcat/bin/setenv.sh", \
      "-stdout", "/kb/deployment/services/workspace/tomcat/logs/catalina.out", \
      "-stdout", "/kb/deployment/services/workspace/tomcat/logs/access.log", \
      "/usr/share/tomcat9/bin/catalina.sh", "run" ]
