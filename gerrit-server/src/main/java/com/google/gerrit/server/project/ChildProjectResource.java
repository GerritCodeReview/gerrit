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

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class ChildProjectResource implements RestResource {
  public static final TypeLiteral<RestView<ChildProjectResource>> CHILD_PROJECT_KIND =
      new TypeLiteral<RestView<ChildProjectResource>>() {};

  private final ProjectResource parent;
  private final ProjectControl child;

  public ChildProjectResource(ProjectResource parent, ProjectControl child) {
    this.parent = parent;
    this.child = child;
  }

  public ProjectResource getParent() {
    return parent;
  }

  public ProjectControl getChild() {
    return child;
  }

  public boolean isDirectChild() {
    ProjectState firstParent =
        Iterables.getFirst(child.getProjectState().parents(), null);
    return firstParent != null
        && parent.getNameKey().equals(firstParent.getProject().getNameKey());
  }
}
