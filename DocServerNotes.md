This branch is a proof of concept for adding a html documentation server to
the workspace service. It's essentially untested and the code could use some
prettying up.

Notes:
* Needs logging that integrates into the regular WSS logger.
* Not totally sure if requiring a server restart to update docs is the best
  idea... on the other hand it makes things very simple deploy wise. Could
  always parameterize the docs location and have the server pull from files
  on disk rather than the WAR file.
* The doc server startup can fail while the WSS succeeds and vice versa.
* Should be completely compatible with the
  [versioned API setup](https://github.com/MrCreosote/workspace_deluxe/blob/dev-multiple_api_expt/multipleAPInotes.md)

Example usage:

	crusherofheads@icrushdeheads:~/localgit/workspace_deluxe/lib$ ipython 
	In [1]: from biokbase.workspace.client import Workspace
	In [2]: ws = Workspace('http://localhost:7058', user_id='kbasetest', password='foo')

	In [3]: ws.ver()
	Out[3]: u'0.3.5'

	In [4]: import requests
	In [6]: requests.get('http://localhost:7058/docs/').content
	Out[6]: '<html>\n<p>API Docs:<p>\n\n<a href="javadoc/index.html">JavaDoc</a><br/>\n
	<a href="workspace.html">Perl Docs</a><br/>\n<a href="workspace.spec">Workspace KIDL spec file</a><br/>\n
	<a href="RELEASE_NOTES.txt">Release notes</a><br/>\n\n\n</html>'

	In [9]: requests.get('http://localhost:7058/docs/workspace.html').content[0:700] 
	Out[9]: '<?xml version="1.0" ?>\n<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
	"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">\n
	<html xmlns="http://www.w3.org/1999/xhtml">\n<head>\n<title></title>\n
	<meta http-equiv="content-type" content="text/html; charset=utf-8" />\n
	<link rev="made" href="mailto:crusherofheads@icrushdeheads.(none)" />\n
	</head>\n\n<body style="background-color: white">\n\n\n\n<ul id="index">\n
	<li><a href="#NAME">NAME</a></li>\n  <li><a href="#DESCRIPTION">DESCRIPTION</a>\n
	<ul>\n      <li><a href="#ver">ver</a></li>\n
	<li><a href="#create_workspace">create_workspace</a></li>\n
	<li><a href="#alter_workspace_metadata">alter_workspace_metadata</a></li>\n

	In [10]: requests.get('http://localhost:7058/docs/foo').content
	Out[10]: '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
	"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
	<html xmlns="http://www.w3.org/1999/xhtml"><head>
	<title>GlassFish Server Open Source Edition 3.1.2.2 - Error report</title>
	*snip*
	<body><h1>HTTP Status 404 - /foo</h1><hr/><p><b>type</b> Status report</p>
	<p><b>message</b>/foo</p><p><b>description</b>
	The requested resource (/foo) is not available.</p><hr/>
	<h3>GlassFish Server Open Source Edition 3.1.2.2</h3></body></html>'

