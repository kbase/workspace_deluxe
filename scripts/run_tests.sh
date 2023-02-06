#!/bin/sh

set -e

docker-compose build
docker-compose up &
# HACK!
sleep 20
python -m pytest workspace_container_test.py