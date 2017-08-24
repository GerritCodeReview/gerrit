// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitResource implements RestResource {
  public static final TypeLiteral<RestView<CommitResource>> COMMIT_KIND =
      new TypeLiteral<RestView<CommitResource>>() {};

  private final ProjectResource project;
  private final RevCommit commit;

  public CommitResource(ProjectResource project, RevCommit commit) {
    this.project = project;
    this.commit = commit;
  }

  public ProjectControl getProject() {
    return project.getControl();
  }

  public RevCommit getCommit() {
    return commit;
  }
}
