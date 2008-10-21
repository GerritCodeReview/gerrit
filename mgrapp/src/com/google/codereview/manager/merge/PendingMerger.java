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

import com.google.codereview.internal.PendingMerge.PendingMergeRequest;
import com.google.codereview.internal.PendingMerge.PendingMergeResponse;
import com.google.codereview.internal.PostBranchUpdate.PostBranchUpdateRequest;
import com.google.codereview.internal.PostBranchUpdate.PostBranchUpdateResponse;
import com.google.codereview.internal.PostBuildResult.PostBuildResultRequest;
import com.google.codereview.internal.PostBuildResult.PostBuildResultResponse;
import com.google.codereview.internal.PostMergeResult.PostMergeResultRequest;
import com.google.codereview.internal.PostMergeResult.PostMergeResultResponse;
import com.google.codereview.internal.SubmitBuild.SubmitBuildRequest;
import com.google.codereview.internal.SubmitBuild.SubmitBuildResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.StopProcessingException;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.MutableBoolean;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.lib.ObjectId;

/** Merges changes from branches with changes waiting to be merged. */
public class PendingMerger implements Runnable {
  private static final Log LOG = LogFactory.getLog(PendingMerger.class);

  private static final PendingMergeRequest NEXT_REQ =
      PendingMergeRequest.getDefaultInstance();

  private final Backend server;
  private final BranchUpdater updater;

  public PendingMerger(final Backend be) {
    server = be;
    updater = new BranchUpdater(server);
  }

  public void run() {
    try {
      runImpl();
    } catch (RuntimeException err) {
      LOG.fatal("Unexpected runtime failure", err);
      throw err;
    } catch (Error err) {
      LOG.fatal("Unexpected runtime failure", err);
      throw err;
    }
  }

  private void runImpl() {
    boolean tryAnother;
    do {
      tryAnother = next();
    } while (tryAnother);
  }

  private boolean next() {
    final MutableBoolean tryAnother = new MutableBoolean();
    final SimpleController ctrl = new SimpleController();
    server.getMergeService().nextPendingMerge(ctrl, NEXT_REQ,
        new RpcCallback<PendingMergeResponse>() {
          public void run(final PendingMergeResponse rsp) {
            tryAnother.value = merge(rsp);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("nextPendingMerge failed: " + ctrl.errorText());
      tryAnother.value = false;
    }
    return tryAnother.value;
  }

  private boolean merge(final PendingMergeResponse rsp) {
    final PendingMergeResponse.CodeType sc = rsp.getStatusCode();
    if (sc == PendingMergeResponse.CodeType.QUEUE_EMPTY) {
      return false;
    }

    if (sc == PendingMergeResponse.CodeType.MERGE_READY) {
      mergeImpl(rsp);
      return true;
    }

    throw new StopProcessingException("unknown status " + sc.name());
  }

  protected void mergeImpl(final PendingMergeResponse rsp) {
    final MergeOp mo = new MergeOp(server, rsp);
    final PostMergeResultRequest result = mo.merge();

    send(result);

    if (mo.getMergeTip() != null && !mo.getNewChanges().isEmpty()) {
      final SubmitBuildRequest.Builder b = SubmitBuildRequest.newBuilder();
      b.setBranchKey(rsp.getDestBranchKey());
      b.setRevisionId(mo.getMergeTip().name());
      for (final CodeReviewCommit c : mo.getNewChanges()) {
        if (c.patchsetKey != null) {
          b.addNewChange(c.patchsetKey);
        }
      }
      send(b.build(), mo.getMergeTip().getId());
    } else {
      final PostBranchUpdateRequest.Builder b;

      b = PostBranchUpdateRequest.newBuilder();
      b.setBranchKey(rsp.getDestBranchKey());
      // Don't mark any changes merged.
      send(b.build());
    }
  }

  private void send(final PostMergeResultRequest msg) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("\n" + msg);
    }

    final SimpleController ctrl = new SimpleController();
    server.getMergeService().postMergeResult(ctrl, msg,
        new RpcCallback<PostMergeResultResponse>() {
          public void run(final PostMergeResultResponse rsp) {
          }
        });
    if (ctrl.failed()) {
      LOG.warn("postMergeResult failed: " + ctrl.errorText());
    }
  }

  private void send(final SubmitBuildRequest msg, final ObjectId id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("\n" + msg);
    }

    final SimpleController ctrl = new SimpleController();
    server.getBuildService().submitBuild(ctrl, msg,
        new RpcCallback<SubmitBuildResponse>() {
          public void run(final SubmitBuildResponse rsp) {
            scheduleBuild(rsp, id);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("submitBuild failed: " + ctrl.errorText());
    }
  }

  private void scheduleBuild(final SubmitBuildResponse rsp, final ObjectId id) {
    final int buildId = rsp.getBuildId();
    LOG.debug("Merge commit " + id.name() + " is build " + buildId);

    // For now assume the build was successful.
    //
    final PostBuildResultRequest.Builder req;
    req = PostBuildResultRequest.newBuilder();
    req.setBuildId(buildId);
    req.setBuildStatus(PostBuildResultRequest.ResultType.SUCCESS);
    final SimpleController ctrl = new SimpleController();
    server.getBuildService().postBuildResult(ctrl, req.build(),
        new RpcCallback<PostBuildResultResponse>() {
          public void run(final PostBuildResultResponse rsp) {
            updater.updateBranch(rsp);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("postBuildResult failed: " + ctrl.errorText());
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
