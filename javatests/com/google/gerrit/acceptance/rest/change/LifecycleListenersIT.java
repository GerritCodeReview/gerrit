// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractLifecycleListenersTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class LifecycleListenersIT extends AbstractLifecycleListenersTest {
  @Inject private InvocationCheck invocationCheck;

  @Before
  public void before() {
    invocationCheck.setStartInvoked(false);
    invocationCheck.setStopInvoked(false);
  }

  @Test
  public void lifecycleListenerSuccessfulInvocation() throws Exception {
    try (AutoCloseable ignored = installPlugin("my-plugin", SimpleModule.class)) {
      RestResponse response = adminRestSession.get("/changes/?--my-plugin--opt&q=status:open");
      response.assertOK();
      assertTrue(invocationCheck.isStartInvoked());
      assertTrue(invocationCheck.isStopInvoked());
    }
  }

  @Test
  public void lifecycleListenerUnsuccessfulInvocation() throws Exception {
    try (AutoCloseable ignored = installPlugin("my-plugin", SimpleModule.class)) {
      RestResponse response = adminRestSession.get("/projects/");
      response.assertOK();
      assertFalse(invocationCheck.isStartInvoked());
      assertFalse(invocationCheck.isStopInvoked());
    }
  }
}
