FROM kbase/sdkbase2 as build

RUN pip install configobj && \
    cd /tmp && \
    git clone https://github.com/kbase/jars

COPY . /tmp/workspace_deluxe
RUN \
    cd /tmp/workspace_deluxe && \
    make docker_deps

FROM kbase/kb_jre

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

COPY --from=build /tmp/workspace_deluxe/deployment/ /kb/deployment/

RUN /usr/bin/tomcat8-instance-create /kb/deployment/services/workspace/tomcat && \
    mv /kb/deployment/services/workspace/WorkspaceService.war /kb/deployment/services/workspace/tomcat/webapps/ROOT.war && \
    rm -rf /kb/deployment/services/workspace/tomcat/webapps/ROOT

COPY --from=build /tmp/workspace_deluxe/scripts/entrypoint.sh/ /kb/deployment/bin/
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
ENTRYPOINT [ "/kb/deployment/bin/entrypoint.sh" ]
WORKDIR /kb/deployment/services/workspace/tomcat

