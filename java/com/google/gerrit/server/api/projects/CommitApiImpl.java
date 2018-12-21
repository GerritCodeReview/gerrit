// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.api.projects.CommitApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.restapi.change.CherryPickCommit;
import com.google.gerrit.server.restapi.project.CommitIncludedIn;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class CommitApiImpl implements CommitApi {
  public interface Factory {
    CommitApiImpl create(CommitResource r);
  }

  private final Changes changes;
  private final CherryPickCommit cherryPickCommit;
  private final CommitIncludedIn includedIn;
  private final CommitResource commitResource;

  @Inject
  CommitApiImpl(
      Changes changes,
      CherryPickCommit cherryPickCommit,
      CommitIncludedIn includedIn,
      @Assisted CommitResource commitResource) {
    this.changes = changes;
    this.cherryPickCommit = cherryPickCommit;
    this.includedIn = includedIn;
    this.commitResource = commitResource;
  }

  @Override
  public ChangeApi cherryPick(CherryPickInput input) throws RestApiException {
    try {
      return changes.id(cherryPickCommit.apply(commitResource, input)._number);
    } catch (Exception e) {
      throw asRestApiException("Cannot cherry pick", e);
    }
  }

  @Override
  public IncludedInInfo includedIn() throws RestApiException {
    try {
      return includedIn.apply(commitResource);
    } catch (Exception e) {
      throw asRestApiException("Could not extract IncludedIn data", e);
    }
  }
}
