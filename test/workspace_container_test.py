from lib.biokbase.workspace.client import Workspace
import pytest
import requests
import json
import time

""" workspace_container_test.py

Very simple tests to ensure that local workspace and auth2 servers are functioning correctly.
Requires the python libraries `pytest` and `requests` to be installed.

Assumes that the workspace and auth2 are running locally on ports 8080 and 7058 respectively.

Use the wrapper shell script, `run_tests.sh`, to create the necessary set up and run the tests:

sh scripts/run_tests.sh

"""

WORKSPACE_VERSION = "0.14.fail"

AUTH_URL = "http://localhost:8080"
WS_URL = "http://localhost:7058"
USER_NAME = "some_dull_user"
WS_NAME = "my_cool_new_workspace"
WAIT_TIMES = [1, 2, 5, 10, 30]


@pytest.fixture(scope="module")
def ready():
    wait_for_auth()
    wait_for_ws()
    
    yield

def wait_for_auth():
    print("waiting for auth service...")
    for t in WAIT_TIMES:
        try: 
            res = requests.get(AUTH_URL)
            res.raise_for_status()
            return
        except Exception as e:
            print(f"Failed to connect to auth, waiting {t} sec and trying again:\n\t{e}")
        time.sleep(t)
    raise Exception(f"Couldn't connect to the auth after {len(WAIT_TIMES)} attempts")


def wait_for_ws():
    print("waiting for workspace service...")
    ws = Workspace(WS_URL)
    for t in WAIT_TIMES:
        try:
            ws.ver()
            return
        except Exception as e:
            print(f"Failed to connect to workspace, waiting {t} sec and trying again:\n\t{e}")
        time.sleep(t)
    raise Exception(f"Couldn't connect to the workspace after {len(WAIT_TIMES)} attempts")


def test_create_user_create_workspace(ready):
    """create a user and then create a workspace for that user"""
    token = create_auth2_user_token()
    get_ws_version(token)
    create_read_workspace(token)


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

    # create the toke
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


def test_get_docs(ready) -> None:
    """check that the workspace documentation can be accessed"""
    response = requests.get(WS_URL + "/docs/")
    assert response.status_code == 200
    assert response.text.find("KBase Workspace Service Manual") != -1
    assert response.text.find("KBase Workspace " + WORKSPACE_VERSION + " documentation") != -1
