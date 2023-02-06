#!/bin/sh
#
# script to run the python workspace_container_test.py on GitHub Actions.
# see .github/workflows/test.yml

docker-compose build
docker-compose up &
# ugly way to wait for all the services to be ready
sleep 60

python -m pytest workspace_container_test.py
docker-compose down
