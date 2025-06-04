// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project.labels;

import static com.google.gerrit.server.project.LabelResource.LABEL_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ProjectLabelsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), LABEL_KIND);

    child(PROJECT_KIND, "labels").to(LabelsCollection.class);
    create(LABEL_KIND).to(CreateLabel.class);
    get(LABEL_KIND).to(GetLabel.class);
    put(LABEL_KIND).to(SetLabel.class);
    delete(LABEL_KIND).to(DeleteLabel.class);
    postOnCollection(LABEL_KIND).to(PostLabels.class);
    post(PROJECT_KIND, "labels:review").to(PostLabelsReview.class);
  }
}
