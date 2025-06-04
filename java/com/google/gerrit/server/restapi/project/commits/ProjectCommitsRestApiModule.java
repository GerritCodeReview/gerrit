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

package com.google.gerrit.server.restapi.project.commits;

import static com.google.gerrit.server.project.CommitResource.COMMIT_KIND;
import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.CherryPickCommit;

public class ProjectCommitsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), COMMIT_KIND);

    child(PROJECT_KIND, "commits").to(CommitsCollection.class);
    get(COMMIT_KIND).to(GetCommit.class);
    post(COMMIT_KIND, "cherrypick").to(CherryPickCommit.class);
    get(COMMIT_KIND, "in").to(CommitIncludedIn.class);
  }
}
