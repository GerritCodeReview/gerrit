// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.server.RequestInfo;
import com.google.gerrit.server.RequestListener;
import com.google.gerrit.server.plugincontext.AutoCloseablePluginSetContext;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.inject.Inject;
import org.junit.Test;

public class AutoCloseablePluginContextIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void allExtensionsGetClosed() throws Exception {
    testAllExtensionsGetClosed(/* failOnOpen= */ false, /* failOnClose= */ false);
  }

  @Test
  public void allExtensionsGetClosed_openThrowsException() throws Exception {
    testAllExtensionsGetClosed(/* failOnOpen= */ true, /* failOnClose= */ false);
  }

  @Test
  public void allExtensionsGetClosed_closeThrowsException() throws Exception {
    testAllExtensionsGetClosed(/* failOnOpen= */ false, /* failOnClose= */ true);
  }

  private void testAllExtensionsGetClosed(boolean failOnOpen, boolean failOnClose)
      throws Exception {
    TestRequestListener requestListener1 = new TestRequestListener(failOnOpen, failOnClose);
    TestRequestListener requestListener2 = new TestRequestListener(failOnOpen, failOnClose);
    TestRequestListener requestListener3 = new TestRequestListener(failOnOpen, failOnClose);
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(requestListener1)
            .add(requestListener2)
            .add(requestListener3)) {
      AutoCloseablePluginSetContext<RequestListener> autoCloseablePluginSetContext =
          new AutoCloseablePluginSetContext<>(
              extensionRegistry.getRequestListeners(), PluginMetrics.DISABLED_INSTANCE);
      try (AutoCloseable autoCloseable =
          autoCloseablePluginSetContext.openEach(
              requestListener -> requestListener.onRequest(/* requestinfo= */ null))) {
        assertThat(requestListener1.closed).isFalse();
        assertThat(requestListener2.closed).isFalse();
        assertThat(requestListener3.closed).isFalse();
      }
      assertThat(requestListener1.closed).isTrue();
      assertThat(requestListener2.closed).isTrue();
      assertThat(requestListener3.closed).isTrue();
    }
  }

  private static class TestRequestListener implements RequestListener {
    public boolean closed;

    private final boolean failOnOpen;
    private final boolean failOnClose;

    TestRequestListener(boolean failOnOpen, boolean failOnClose) {
      this.failOnOpen = failOnOpen;
      this.failOnClose = failOnClose;
    }

    @Override
    public void onRequest(RequestInfo requestInfo) {
      if (failOnOpen) {
        throw new RuntimeException("fail on test");
      }
    }

    @Override
    public void close() {
      closed = true;
      if (failOnClose) {
        throw new RuntimeException("fail on test");
      }
    }
  }
}
