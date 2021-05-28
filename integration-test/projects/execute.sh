#! /bin/bash
#
# Copyright (c) 2021 - Yupiik SAS - https://www.yupiik.com
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#


rm -Rf "/tmp/$1" && \
cp -r "$1" "/tmp/$1" && \
cd "/tmp/$1" && \
sed -i "s/project\.version/$3/g" pom.xml && \
mvn clean package -e 1>&2 && \
"./target/$1.graal.bin" "$2"

