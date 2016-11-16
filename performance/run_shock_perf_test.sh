#!/bin/bash
JARS=../../jars/lib/jars
CLASSPATH=.:\
$JARS/kbase/shock/shock-client-0.0.14.jar:\
$JARS/kbase/auth/kbase-auth-0.4.1.jar:\
$JARS/apache_commons/commons-logging-1.1.1.jar:\
$JARS/apache_commons/http/httpclient-4.3.1.jar:\
$JARS/apache_commons/http/httpcore-4.3.jar:\
$JARS/apache_commons/http/httpmime-4.3.1.jar:\
$JARS/jackson/jackson-annotations-2.2.3.jar:\
$JARS/jackson/jackson-core-2.2.3.jar:\
$JARS/jackson/jackson-databind-2.2.3.jar:\
$JARS/apache_commons/commons-io-2.4.jar

javac -cp $CLASSPATH us/kbase/workspace/performance/shockclient/SaveAndGetFromShock.java
java -cp $CLASSPATH us.kbase.workspace.performance.shockclient.SaveAndGetFromShock $@
