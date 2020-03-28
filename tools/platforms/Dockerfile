# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This Dockerfile upgrades Git version to version >2.6.
# This is needed for Git  wire protocol 2 tests to pass, because
# this test is using Git client, and support for new Git protocol
# was added in 2.18.0. Git version shipped in Ubuntu 16.04: 2.7.4
# and Git version shipped in Ubuntu 18.04: 2.17.1.
#
# To authenticate to GCloud registry:
# gcloud auth configure-docker
#
# To build the docker image:
# docker build -t gcr.io/api-project-164060093628/rbe-ubuntu18-04 .
#
# To test the docker image:
# docker run -it gcr.io/api-project-164060093628/rbe-ubuntu18-04 bash
# git version
# git version 2.26.0
#
# To publish the docker image to GCloud registry:
# docker push gcr.io/api-project-164060093628/rbe-ubuntu18-04
#

FROM gcr.io/cloud-marketplace/google/rbe-ubuntu18-04:latest

# Install Git >2.6
RUN add-apt-repository ppa:git-core/ppa && \
    apt-get -y update && \
    apt-get -y install git && \
    apt-get clean
