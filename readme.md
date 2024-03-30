## KBase Workspace Service

The Workspace Service (WSS) is a language independent remote storage
and retrieval system for KBase typed objects (TO) defined with the KBase
Interface Description Language (KIDL). It has the following primary features:

* Immutable storage of TOs with
  * user defined metadata
  * data provenance
* Versioning of TOs
* Referencing from TO to TO
* Typechecking of all saved objects against a KIDL specification
* Collecting typed objects into a workspace
* Sharing workspaces with specific KBase users or the world
* Freezing and publishing workspaces
* Serverside extraction of portions of an object

### Getting documentation

The WSS documentation describes how to install, configure, run, develop, and
use the WSS. The easiest way to read the documentation is to find an already
built instance online:

* [KBase Continuous Integration](https://ci.kbase.us/services/ws/docs/)
* [KBase Appdev](https://appdev.kbase.us/services/ws/docs/)
* [KBase Production](https://kbase.us/services/ws/docs/)

The documentation can also be read on Github as
[restructured text (reST) files](https://github.com/kbase/workspace_deluxe/tree/master/docsource).

However, Github does not display tables of contents and cross references don't
work, so navigation isn't particularly friendly. Also, the API documentation
isn't available.

The better but slightly harder alternative is to build the documentation
yourself.

### Building documentation

This documentation assumes the documentation build occurs on Ubuntu 18.04LTS,
but things should work similarly on other distributions.

The build requires:

Java JDK 11

[Python](https://www.python.org) [Sphinx](http://sphinx-doc.org/) 1.3+

Clone the workspace_deluxe repos:

    bareubuntu@bu:~/ws$ git clone https://github.com/kbase/workspace_deluxe

Build the documentation:

    bareubuntu@bu:~/ws$ cd workspace_deluxe/
    bareubuntu@bu:~/ws/workspace_deluxe$ ./gradlew buildDocs

The build directory is `build/docs`.

### Notes on GitHub Actions automated tests

The GHA tests do not run the WorkspaceLongTest or JSONRPCLongTest test classes
because they take too long to run.

Therefore, run the full test suite locally at least prior to every release.

### Downloading the Docker image

The latest `workspace_deluxe` image is available from the GitHub Container Repository:

    docker login ghcr.io
    docker pull ghcr.io/kbase/workspace_deluxe:latest

### Setting up a local instance

The included [docker-compose file](docker-compose.yml) allows developers to stand up a local
workspace instance with an [auth2](http://github.com/kbase/auth2) instance in test mode:

    docker compose up --build -d

The workspace has started when the logs show a line that looks like

    <timestamp> INFO [main] org.apache.catalina.startup.Catalina.start Server startup in 3198ms

Developers can then create a user and token using the auth2 service and use one of the clients
in the [`lib/`](lib/) directory to interact with the workspace. See
[workspace_container_test.py](scripts/workspace_container_test.py) as an example of this process.

See the [auth2 documentation](http://github.com/kbase/auth2) for details of the test mode
interface.
