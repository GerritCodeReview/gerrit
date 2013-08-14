// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.Inject;

public class GetConfig implements RestReadView<ProjectResource> {

  private final TransferConfig config;

  @Inject
  public GetConfig(TransferConfig config) {
    this.config = config;
  }

  @Override
  public ConfigInfo apply(ProjectResource resource) {
    return new ConfigInfo(resource.getControl().getProjectState(), config);
  }
}
