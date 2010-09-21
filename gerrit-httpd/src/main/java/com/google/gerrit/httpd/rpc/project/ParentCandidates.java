// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.RetrieveParentCandidates;
import com.google.inject.Inject;

import java.util.List;

public class ParentCandidates extends Handler<List<Project.NameKey>> {
  interface Factory {
    ParentCandidates create();
  }

  private final RetrieveParentCandidates retrieveParentCandidates;

  @Inject
  ParentCandidates(final RetrieveParentCandidates retrieveParentCandidates) {
    this.retrieveParentCandidates = retrieveParentCandidates;
  }

  @Override
  public List<Project.NameKey> call() throws Exception {
    return retrieveParentCandidates.getParentCandidates();
  }

}
