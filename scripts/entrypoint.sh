#!/bin/sh


if [ ! -z "$CONF_URL" ] ; then
    ENV="-env $CONF_URL"
fi

if [ -e "/run/secrets/auth_data" ] ; then
    ENVHEADER="--env-header /run/secrets/auth_data"
fi

if [ ! -z "$SECRET" ] ; then
    ENVHEADER="--env-header /run/secrets/$SECRET"
fi

if [ ! -z "$WAIT_ON" ] ; then
    WAIT="-timeout 150s"
    for h in $(echo $WAIT_ON|sed 's/,/ /g') ; do
        WAIT="$WAIT -wait tcp://$h"
    done
fi

/kb/deployment/bin/dockerize  \
    $ENV \
    $ENVHEADER \
    $WAIT \
    -validate-cert=false \
    -template /kb/deployment/conf/.templates/deployment.cfg.templ:/kb/deployment/conf/deployment.cfg \
    -template /kb/deployment/conf/.templates/server.xml.templ:/kb/deployment/services/workspace/tomcat/conf/server.xml \
    -template /kb/deployment/conf/.templates/tomcat-users.xml.templ:/kb/deployment/services/workspace/tomcat/conf/tomcat-users.xml \
    -template /kb/deployment/conf/.templates/logging.properties.templ:/kb/deployment/services/workspace/tomcat/conf/logging.properties \
    -template /kb/deployment/conf/.templates/setenv.sh.templ:/kb/deployment/services/workspace/tomcat/bin/setenv.sh \
    -stdout /kb/deployment/services/workspace/tomcat/logs/catalina.out \
    -stdout /kb/deployment/services/workspace/tomcat/logs/access.log, \
    /usr/share/tomcat8/bin/catalina.sh run

