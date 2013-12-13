gen_java_types -o . workspaceService.spec
javac -cp /kb/dev_container/modules/jars/lib/jars/jackson/jackson-core-2.2.3.jar:/kb/dev_container/modules/jars/lib/jars/jackson/jackson-annotations-2.2.3.jar:/kb/dev_container/modules/jars/lib/jars/jackson/jackson-databind-2.2.3.jar:/kb/dev_container/modules/jars/lib/jars/kbase/auth/kbase-auth-1380919426-d35c17d.jar:src src/us/kbase/workspaceservice/WorkspaceServiceClient.java
jar cf Workspace0_0_5.jar -C src us
rm -r src
rm -r lib
