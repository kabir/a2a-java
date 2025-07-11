#!/bin/sh

echo "Figuring out where I am!"
pwd
echo "Files in directory"
ls -al

cd quarkus-app

# Start server
java -Dquarkus.profile=dev -jar quarkus-run.jar