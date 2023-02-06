from lib.biokbase.workspace.client import Workspace
import pytest
import requests
import json

WORKSPACE_VERSION = "0.14.1"

AUTH_URL = "http://localhost:8080"
WS_URL = "http://localhost:7058"
USER_NAME = "some_dull_user"
WS_NAME = "my_cool_new_workspace"

def test_create_user_create_workspace() -> None:
  user_token = create_auth2_user_token()
  get_ws_version(user_token)
  create_read_workspace(user_token)

def create_auth2_user_token() -> str:
  user_json = json.dumps({"user": USER_NAME, "display": "Blah blah"})
  response = requests.post(
    AUTH_URL + "/testmode/api/V2/testmodeonly/user",
    data=user_json,
    headers={"content-type":"application/json"}
  )

  print(response.text)
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
  return token_data["token"]

def get_ws_version(token: str) -> None:
  ws = Workspace(WS_URL, token=token)
  assert ws.ver() == WORKSPACE_VERSION

def create_read_workspace(token: str) -> None:
  ws = Workspace(WS_URL, token=token)
  new_ws = ws.create_workspace({'workspace': WS_NAME})
  assert new_ws[1] == WS_NAME
  assert new_ws[2] == USER_NAME
  assert ws.get_workspace_info({ "id": new_ws[0] }) == new_ws

def test_get_docs() -> None:
  response = requests.get(WS_URL + "/docs/")
  assert response.status_code == 200
  assert response.text.find("KBase Workspace Service Manual") != -1
  assert response.text.find("KBase Workspace " + WORKSPACE_VERSION + " documentation") != -1
