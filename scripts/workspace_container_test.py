from lib.biokbase.workspace.client import Workspace
import pytest
import requests
import json

""" workspace_container_test.py

Very simple tests to ensure that local workspace and auth2 servers are functioning correctly.
Requires the python libraries `pytest` and `requests` to be installed.

Assumes that the workspace and auth2 are running locally on ports 8080 and 7058 respectively.

Use the wrapper shell script, `run_tests.sh`, to create the necessary set up and run the tests:

sh scripts/run_tests.sh

"""

WORKSPACE_VERSION = "0.14.1"

AUTH_URL = "http://localhost:8080"
WS_URL = "http://localhost:7058"
USER_NAME = "some_dull_user"
WS_NAME = "my_cool_new_workspace"

def test_create_user_create_workspace() -> None:
  """create a user and then create a workspace for that user"""
  user_token = create_auth2_user_token()
  get_ws_version(user_token)
  create_read_workspace(user_token)

def create_auth2_user_token() -> str:
  """create a user and generate a token for that user"""
  # create a new user
  user_json = json.dumps({"user": USER_NAME, "display": "Blah blah"})
  response = requests.post(
    AUTH_URL + "/testmode/api/V2/testmodeonly/user",
    data=user_json,
    headers={"content-type":"application/json"}
  )
  assert response.status_code == 200
  user_data = response.json()
  assert 'created' in user_data

  # create the token
  token_json = json.dumps({ "user": USER_NAME, "type": "Login" })
  response = requests.post(
    AUTH_URL + "/testmode/api/V2/testmodeonly/token",
    data=token_json,
    headers={"content-type":"application/json"}
  )
  assert response.status_code == 200
  token_data = response.json()
  assert 'created' in token_data
  assert 'token' in token_data
  return token_data["token"]

def get_ws_version(token: str) -> None:
  """get the current workspace version"""
  ws = Workspace(WS_URL, token=token)
  assert ws.ver() == WORKSPACE_VERSION

def create_read_workspace(token: str) -> None:
  """create a new workspace and then get the workspace information"""
  ws = Workspace(WS_URL, token=token)
  new_ws = ws.create_workspace({'workspace': WS_NAME})
  assert new_ws[1] == WS_NAME
  assert new_ws[2] == USER_NAME
  assert ws.get_workspace_info({ "id": new_ws[0] }) == new_ws

def test_get_docs() -> None:
  """check that the workspace documentation can be accessed"""
  response = requests.get(WS_URL + "/docs/")
  assert response.status_code == 200
  assert response.text.find("KBase Workspace Service Manual") != -1
  assert response.text.find("KBase Workspace " + WORKSPACE_VERSION + " documentation") != -1