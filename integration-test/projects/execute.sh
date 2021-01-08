#! /bin/bash

rm -Rf "/tmp/$1" && \
cp -r "$1" "/tmp/$1" && \
cd "/tmp/$1" && \
sed -i "s/project\.version/$3/g" pom.xml && \
mvn clean package 1>&2 && \
"./target/$1.graal.bin" "$2"

