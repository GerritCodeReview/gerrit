// Copyright 2008 Google Inc.
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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.GerritServer;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  private final GerritServer server;

  public PatchDetailServiceImpl(final GerritServer gs) {
    server = gs;
  }

  public void sideBySidePatchDetail(final Patch.Key key,
      final AsyncCallback<SideBySidePatchDetail> callback) {
    final RepositoryCache rc = server.getRepositoryCache();
    if (rc == null) {
      callback.onFailure(new Exception("No Repository Cache configured"));
      return;
    }
    run(callback, new SideBySidePatchDetailAction(rc, key));
  }

  public void unifiedPatchDetail(final Patch.Key key,
      final AsyncCallback<UnifiedPatchDetail> callback) {
    run(callback, new UnifiedPatchDetailAction(key));
  }
}
