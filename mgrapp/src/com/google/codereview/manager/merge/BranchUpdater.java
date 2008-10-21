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

package com.google.codereview.manager.merge;

import com.google.codereview.internal.PostBranchUpdate.PostBranchUpdateRequest;
import com.google.codereview.internal.PostBranchUpdate.PostBranchUpdateResponse;
import com.google.codereview.internal.PostBuildResult.PostBuildResultResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.rpc.SimpleController;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BranchUpdater {
  private static final Log LOG = LogFactory.getLog(BranchUpdater.class);

  private final Backend server;

  public BranchUpdater(final Backend be) {
    server = be;
  }

  public void updateBranch(final PostBuildResultResponse buildInfo) {
    if (new UpdateOp(server.getRepositoryCache(), buildInfo).update()) {
      final PostBranchUpdateRequest.Builder req;

      req = PostBranchUpdateRequest.newBuilder();
      req.setBranchKey(buildInfo.getDestBranchKey());
      req.addAllNewChange(buildInfo.getNewChangeList());
      send(req.build());
    } else {
      final PostBranchUpdateRequest.Builder req;

      req = PostBranchUpdateRequest.newBuilder();
      req.setBranchKey(buildInfo.getDestBranchKey());
      // Don't mark any changes merged.
      send(req.build());
    }
  }

  private void send(final PostBranchUpdateRequest msg) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("\n" + msg);
    }

    final SimpleController ctrl = new SimpleController();
    server.getMergeService().postBranchUpdate(ctrl, msg,
        new RpcCallback<PostBranchUpdateResponse>() {
          public void run(final PostBranchUpdateResponse rsp) {
          }
        });
    if (ctrl.failed()) {
      LOG.warn("postBranchUpdate failed: " + ctrl.errorText());
    }
  }
}
