// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.IncludedIn;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ChangeIncludedIn implements RestReadView<ChangeResource> {
  private PatchSetUtil psUtil;
  private IncludedIn includedIn;

  @Inject
  ChangeIncludedIn(PatchSetUtil psUtil, IncludedIn includedIn) {
    this.psUtil = psUtil;
    this.includedIn = includedIn;
  }

  @Override
  public IncludedInInfo apply(ChangeResource rsrc) throws RestApiException, IOException {
    PatchSet ps = psUtil.current(rsrc.getNotes());
    return includedIn.apply(rsrc.getProject(), ps.getRevision().get());
  }
}
