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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.TypeLiteral;

public class CheckResource implements RestResource {
  public static final TypeLiteral<RestView<CheckResource>> CHECK_KIND =
      new TypeLiteral<RestView<CheckResource>>() {};

  private final RevisionResource revisionResource;
  private final Check check;

  public CheckResource(RevisionResource revisionResource, Check c) {
    this.revisionResource = revisionResource;
    this.check = c;
  }

  public RevisionResource getRevisionResource() {
    return revisionResource;
  }

  public PatchSet getPatchSet() {
    return revisionResource.getPatchSet();
  }

  public Check getCheck() {
    return check;
  }

  public String getCheckerUUID() {
    return check.key().checkerUUID();
  }

  public Project.NameKey getProject() {
    return revisionResource.getProject();
  }

  public Change.Id getChangeId() {
    return revisionResource.getChange().getId();
  }
}
