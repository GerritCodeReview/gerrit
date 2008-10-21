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

import com.google.codereview.internal.PruneBundles.PruneBundlesRequest;
import com.google.codereview.internal.PruneBundles.PruneBundlesResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.StopProcessingException;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.MutableBoolean;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Deletes invalid bundles from the data store. */
public class BundlePruner implements Runnable {
  private static final Log LOG = LogFactory.getLog(BundlePruner.class);

  private static final PruneBundlesRequest NEXT_REQ =
      PruneBundlesRequest.getDefaultInstance();

  private final Backend server;

  public BundlePruner(final Backend be) {
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
    server.getBundleStoreService().pruneBundles(ctrl, NEXT_REQ,
        new RpcCallback<PruneBundlesResponse>() {
          public void run(final PruneBundlesResponse rsp) {
            tryAnother.value = prune(rsp);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("pruneBundles failed: " + ctrl.errorText());
      tryAnother.value = false;
    }
    return tryAnother.value;
  }

  private boolean prune(final PruneBundlesResponse rsp) {
    final PruneBundlesResponse.CodeType sc = rsp.getStatusCode();

    if (sc == PruneBundlesResponse.CodeType.QUEUE_EMPTY) {
      return false;
    }

    if (sc == PruneBundlesResponse.CodeType.BUNDLES_PRUNED) {
      return true;
    }

    throw new StopProcessingException("unknown status " + sc.name());
  }
}
