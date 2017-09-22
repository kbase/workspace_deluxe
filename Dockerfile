FROM kbase/kb_jre

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

RUN cd /usr/local && \
    wget  http://download.java.net/glassfish/v3/release/glassfish-v3.zip && \
    unzip glassfish-v3.zip && \
    rm glassfish-v3.zip

ENV GLASSFISH /usr/local/glassfishv3

RUN mkdir /kb

COPY deployment/ /kb/deployment/

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/auth2.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.0-rc1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="Steve Chan sychan@lbl.gov"

# ENTRYPOINT [ "/kb/deployment/bin/entrypoint.sh" ]