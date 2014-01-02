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

package com.google.gerrit.httpd.raw;

import com.google.common.base.Objects;
import com.google.gerrit.httpd.resources.ResourceKey;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.lib.ObjectId;

public class ProjectDocResourceKey implements ResourceKey {
  private final Project.NameKey project;
  private final String resource;
  private final ObjectId revId;

  ProjectDocResourceKey(Project.NameKey project, String r, ObjectId revId) {
    this.project = project;
    this.resource = r;
    this.revId = revId;
  }

  @Override
  public int weigh() {
    return resource.length() * 2;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(project, resource, revId);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof ProjectDocResourceKey) {
      ProjectDocResourceKey rk = (ProjectDocResourceKey) other;
      return project.equals(rk.project) && resource.equals(rk.resource)
          && revId.equals(rk.revId);
    }
    return false;
  }
}
