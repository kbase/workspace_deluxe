#!/usr/bin/env sh
cd /kb/deployment

bin/glassfish_administer_service.py --verbose --admin $GLASSFISH/bin/asadmin --domain Workspace \
        --domain-dir /kb/deployment/services/workspace/glassfish_domain \
        --war /kb/deployment/services/workspace/WorkspaceService.war --port 7058 --threads 20 \
        --Xms 10000 --Xmx 15000 --properties KB_DEPLOYMENT_CONFIG=$KB_DEPLOYMENT_CONFIG  && \
tail -n 500 -f /kb/deployment/services/workspace/glassfish_domain/Workspace/logs/server.log
