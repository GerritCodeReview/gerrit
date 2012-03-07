// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.SuggestParentCandidates;
import com.google.inject.Inject;

import java.util.List;

public class SuggestParentCandidatesHandler extends Handler<List<Project>> {
  interface Factory {
    SuggestParentCandidatesHandler create();
  }

  private final SuggestParentCandidates suggestParentCandidates;

  @Inject
  SuggestParentCandidatesHandler(final SuggestParentCandidates suggestParentCandidates) {
    this.suggestParentCandidates = suggestParentCandidates;
  }

  @Override
  public List<Project> call() throws Exception {
    return suggestParentCandidates.getProjects();
  }
}
