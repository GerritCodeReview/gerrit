# Copyright (C) 2021 The Android Open Source Project
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

FROM gcr.io/bazel-public/ubuntu2204-java17@sha256:c94269bb0cd79b47700fb7ce86f33ea9cfa0ceee64bb0f12d26345b6bed4877a

# Install openssh-client (ssh-keygen utility)
RUN apt-get -y update && \
    apt-get -y install openssh-client && \
    apt-get clean
