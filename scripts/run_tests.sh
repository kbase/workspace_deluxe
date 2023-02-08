#!/bin/bash
#
# script to run the python workspace_container_test.py locally or on GitHub Actions.
# builds and mounts auth2, workspace, and mongo docker containers.
#
# see .github/workflows/test.yml

docker compose build
docker compose up -d

max_retry=30
counter=0

# - wait for the workspace container to start up -- logs should stop being generated and the
# last line of the logs should be something like
# <timestamp> INFO [main] org.apache.catalina.startup.Catalina.start Server startup in 3811 ms
until docker logs -n 5 workspace_deluxe-workspace-1 2>&1 | grep -q -F -m 1 'org.apache.catalina.startup.Catalina.start Server startup in' > /dev/null
do
    sleep 2
    [[ counter -eq $max_retry ]] && echo "Failed!" && exit 1
    echo "Waiting for the workspace to be ready..."
    ((counter++))
done

python -m pytest workspace_container_test.py
docker-compose down
