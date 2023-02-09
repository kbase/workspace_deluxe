#!/usr/bin/env bash
#
# script to run the python workspace_container_test.py locally or on GitHub Actions.
# builds and mounts auth2, workspace, and mongo docker containers.
#
# see .github/workflows/test.yml

docker compose build
docker compose up -d

compose_up_exit_code=$?

if [ $compose_up_exit_code -ne 0 ]; then
  echo "Error: docker-compose up command failed with exit code $compose_up_exit_code."
  exit $compose_up_exit_code
fi

max_retries=30
counter=0
exit_code=666

# limit the number of retries of the script
while [ $counter -lt $max_retries ]; do
# wait for the workspace container to start up
# logs should stop being generated and the last line of the logs should be something like
# <timestamp> INFO [main] org.apache.catalina.startup.Catalina.start Server startup in 3811 ms
    if docker logs -n 5 "workspace_deluxe-workspace-1" 2>&1 | grep -q -F -m 1 'org.apache.catalina.startup.Catalina.start Server startup in' > /dev/null; then
        # get the path to the 'scripts' directory and add it to the python execution path
        current_dir="$( dirname -- "$( readlink -f -- "$0"; )"; )"
        PYTHONPATH="$current_dir":$PYTHONPATH python -m pytest scripts/workspace_container_test.py
        exit_code=$?
        break
    fi
    echo "Waiting for the workspace to be ready..."
    counter=$(( counter + 1))
    sleep 2
done

if [ $counter -eq $max_retries ]; then
        echo "Workspace server start up not detected after $max_retries retries. Workspace container logs:"
        docker logs -n 5 "workspace_deluxe-workspace-1"
        exit_code=1
fi

docker compose down
exit $exit_code