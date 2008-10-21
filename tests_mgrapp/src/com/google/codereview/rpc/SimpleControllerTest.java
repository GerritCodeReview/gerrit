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

package com.google.codereview.rpc;

import com.google.protobuf.RpcCallback;

import junit.framework.TestCase;

public class SimpleControllerTest extends TestCase {
  public void testDefaultConstructor() {
    final SimpleController sc = new SimpleController();
    assertNull(sc.errorText());
    assertFalse(sc.failed());
    assertFalse(sc.isCanceled());
  }

  public void testNotifyCancelUnsupported() {
    try {
      new SimpleController().notifyOnCancel(new RpcCallback<Object>() {
        public void run(Object parameter) {
          fail("Callback invoked during notifyOnCancel setup");
        }
      });
      fail("notifyOnCancel accepted a callback");
    } catch (UnsupportedOperationException e) {
      // Pass
    }
  }

  public void testStartCancelUnsupported() {
    try {
      new SimpleController().startCancel();
      fail("startCancel did not fail");
    } catch (UnsupportedOperationException e) {
      // Pass
    }
  }

  public void testSetFailed() {
    final String reason = "we failed, yes we did";
    final SimpleController sc = new SimpleController();
    sc.setFailed(reason);
    assertTrue(sc.failed());
    assertSame(reason, sc.errorText());
  }

  public void testResetClearedFailure() {
    final String reason = "we failed, yes we did";
    final SimpleController sc = new SimpleController();
    sc.setFailed(reason);
    sc.reset();
    assertNull(sc.errorText());
    assertFalse(sc.failed());
    assertFalse(sc.isCanceled());
  }
}
