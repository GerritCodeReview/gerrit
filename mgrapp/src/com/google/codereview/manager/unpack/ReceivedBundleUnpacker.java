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

package com.google.codereview.manager.unpack;

import com.google.codereview.internal.NextReceivedBundle.NextReceivedBundleRequest;
import com.google.codereview.internal.NextReceivedBundle.NextReceivedBundleResponse;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleRequest;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.StopProcessingException;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.MutableBoolean;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Obtains newly received bundles and unpacks them into Git. */
public class ReceivedBundleUnpacker implements Runnable {
  private static final Log LOG =
      LogFactory.getLog(ReceivedBundleUnpacker.class);

  private static final NextReceivedBundleRequest NEXT_REQ =
      NextReceivedBundleRequest.getDefaultInstance();

  private final Backend server;

  public ReceivedBundleUnpacker(final Backend be) {
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
    server.getBundleStoreService().nextReceivedBundle(ctrl, NEXT_REQ,
        new RpcCallback<NextReceivedBundleResponse>() {
          public void run(final NextReceivedBundleResponse rsp) {
            tryAnother.value = unpack(rsp);
          }
        });
    if (ctrl.failed()) {
      LOG.warn("nextReceivedBundle failed: " + ctrl.errorText());
      tryAnother.value = false;
    }
    return tryAnother.value;
  }

  private boolean unpack(final NextReceivedBundleResponse rsp) {
    final NextReceivedBundleResponse.CodeType sc = rsp.getStatusCode();
    if (sc == NextReceivedBundleResponse.CodeType.QUEUE_EMPTY) {
      return false;
    }

    if (sc == NextReceivedBundleResponse.CodeType.BUNDLE_AVAILABLE) {
      send(unpackImpl(rsp));
      return true;
    }

    throw new StopProcessingException("unknown status " + sc.name());
  }

  protected UpdateReceivedBundleRequest unpackImpl(
      final NextReceivedBundleResponse rsp) {
    return new UnpackBundleOp(server, rsp).unpack();
  }

  private void send(final UpdateReceivedBundleRequest req) {
    final String key = req.getBundleKey();
    final String sc = req.getStatusCode().name();
    LOG.debug("Bundle " + key + ", status " + sc);

    final SimpleController ctrl = new SimpleController();
    server.getBundleStoreService().updateReceivedBundle(ctrl, req,
        new RpcCallback<UpdateReceivedBundleResponse>() {
          public void run(final UpdateReceivedBundleResponse rsp) {
            final UpdateReceivedBundleResponse.CodeType sc =
                rsp.getStatusCode();
            if (sc != UpdateReceivedBundleResponse.CodeType.UPDATED) {
              ctrl.setFailed(sc.name());
            }
          }
        });
    if (ctrl.failed()) {
      LOG.error("Updating bundle " + key + " failed: " + ctrl.errorText());
    }
  }
}
