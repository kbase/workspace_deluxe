#!/bin/bash
JARS=../../jars/lib/jars
CLASSPATH=.:../src:\
$JARS/kbase/common/kbase-common-0.0.21.jar:\
$JARS/mongo/mongo-java-driver-2.13.3.jar:\
$JARS/apache_commons/commons-io-2.4.jar

javac -cp $CLASSPATH us/kbase/workspace/performance/workspace/PlainJavaTiming.java
java -cp $CLASSPATH us.kbase.workspace.performance.workspace.PlainJavaTiming $@
