FROM kbase/kb_jre

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

RUN cd /usr/local && \
    wget  http://download.oracle.com/glassfish/3.1.2.2/release/glassfish-3.1.2.2.zip && \
    unzip glassfish-3.1.2.2.zip && \
    rm glassfish-3.1.2.2.zip && \
    echo >>glassfish3/glassfish/config/osgi.properties 'jre-1.8=${jre-1.7}'

ENV GLASSFISH /usr/local/glassfish3

RUN mkdir /kb

COPY deployment/ /kb/deployment/

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/workspace_deluxe.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.0-rc1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="Steve Chan sychan@lbl.gov"

ENTRYPOINT [ "/kb/deployment/bin/entrypoint.sh" ]