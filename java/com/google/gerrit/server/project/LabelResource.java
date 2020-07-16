// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class LabelResource implements RestResource {
  public static final TypeLiteral<RestView<LabelResource>> LABEL_KIND =
      new TypeLiteral<RestView<LabelResource>>() {};

  private final ProjectResource project;
  private final LabelType labelType;

  public LabelResource(ProjectResource project, LabelType labelType) {
    this.project = project;
    this.labelType = labelType;
  }

  public ProjectResource getProject() {
    return project;
  }

  public LabelType getLabelType() {
    return labelType;
  }
}
