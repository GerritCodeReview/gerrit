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

package com.google.codereview.manager.prune;

import com.google.codereview.internal.PruneBuilds.PruneBuildsRequest;
import com.google.codereview.internal.PruneBuilds.PruneBuildsResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.StopProcessingException;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.MutableBoolean;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Deletes unnecessary builds from the data store. */
public class BuildPruner implements Runnable {
  private static final Log LOG = LogFactory.getLog(BuildPruner.class);

  private static final PruneBuildsRequest NEXT_REQ =
      PruneBuildsRequest.getDefaultInstance();

  private final Backend server;

  public BuildPruner(final Backend be) {
    server = be;
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
    server.getBuildService().pruneBuilds(ctrl, NEXT_REQ,
        new RpcCallback<PruneBuildsResponse>() {
          public void run(final PruneBuildsResponse rsp) {
            tryAnother.value = prune(rsp);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("pruneBuilds failed: " + ctrl.errorText());
      tryAnother.value = false;
    }
    return tryAnother.value;
  }

  private boolean prune(final PruneBuildsResponse rsp) {
    final PruneBuildsResponse.CodeType sc = rsp.getStatusCode();

    if (sc == PruneBuildsResponse.CodeType.QUEUE_EMPTY) {
      return false;
    }

    if (sc == PruneBuildsResponse.CodeType.BUILDS_PRUNED) {
      return true;
    }

    throw new StopProcessingException("unknown status " + sc.name());
  }
}
