FROM eclipse-temurin:11-jdk as build

WORKDIR /tmp
RUN apt update -y && \
    apt install -y git ca-certificates python3-sphinx

WORKDIR /tmp/workspace

# dependencies take a while to D/L, so D/L & cache before the build so code changes don't cause
# a new D/L
# can't glob *gradle because of the .gradle dir
COPY build.gradle gradlew settings.gradle /tmp/workspace/
COPY gradle/ /tmp/workspace/gradle/
RUN ./gradlew dependencies

# Now build the code
COPY workspace.spec /tmp/workspace/workspace.spec
COPY deployment/ /tmp/workspace/deployment/
COPY docshtml /tmp/workspace/docshtml/
COPY docsource /tmp/workspace/docsource/
COPY lib /tmp/workspace/lib/
COPY src /tmp/workspace/src/
COPY war /tmp/workspace/war/
RUN ./gradlew war

FROM ubuntu:18.04

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

# Must set catalina_base to match location of tomcat8-instance-create dir
# before calling /usr/share/tomcat8/bin/catalina.sh
ENV CATALINA_BASE /kb/deployment/services/workspace/tomcat
ENV KB_DEPLOYMENT_CONFIG /kb/deployment/conf/deployment.cfg
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
    chown -R kbase /kb/deployment && \
    cd /kb/deployment/bin && \
    wget -N https://github.com/kbase/dockerize/raw/master/dockerize-${DOCKERIZE_VERSION}.tar.gz && \
    tar xvzf dockerize-${DOCKERIZE_VERSION}.tar.gz && \
    rm dockerize-${DOCKERIZE_VERSION}.tar.gz

COPY --from=build /tmp/workspace/deployment/ /kb/deployment/

RUN /usr/bin/${TOMCAT_VERSION}-instance-create /kb/deployment/services/workspace/tomcat && \
    rm -rf /kb/deployment/services/workspace/tomcat/webapps/ROOT
COPY --from=build /tmp/workspace/build/libs/workspace_deluxe.war /kb/deployment/services/workspace/tomcat/webapps/ROOT.war

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/workspace_deluxe.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.0-rc1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="KBase developers engage@kbase.us"

# TODO BUILD update to no longer use dockerize and take env vars (e.g. like Collections).
# TODO BUILD Use subsections in the ini file / switch to TOML

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
