// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.filter;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.testing.ConfigSuite;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.junit.Assert;
import org.junit.Test;

public class FilterClassIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config enableFilter() {
    Config cfg = new Config();
    cfg.setStringList(
        "httpd",
        null,
        "filterClass",
        Arrays.asList(
            "com.google.gerrit.acceptance.filter.FakeNoInitParamsFilter",
            "com.google.gerrit.acceptance.filter.FakeMustInitParamsFilter"));
    cfg.setString(
        "filterClass",
        "com.google.gerrit.acceptance.filter.FakeMustInitParamsFilter",
        "PARAM-1",
        "hello");
    cfg.setString(
        "filterClass",
        "com.google.gerrit.acceptance.filter.FakeMustInitParamsFilter",
        "PARAM-2",
        "world");

    return cfg;
  }

  @Test
  public void filterLoad() {
    FakeNoInitParamsFilter fakeNoInitParamsFilter =
        server.getTestInjector().getBinding(FakeNoInitParamsFilter.class).getProvider().get();
    Assert.assertNotNull(fakeNoInitParamsFilter);
    FakeMustInitParamsFilter fakeMustInitParamsFilter =
        server.getTestInjector().getBinding(FakeMustInitParamsFilter.class).getProvider().get();
    Assert.assertNotNull(fakeMustInitParamsFilter);
  }

  @Test
  public void filterInitParams() {
    FakeMustInitParamsFilter fakeMustInitParamsFilter =
        server.getTestInjector().getBinding(FakeMustInitParamsFilter.class).getProvider().get();
    Assert.assertEquals(2, fakeMustInitParamsFilter.getInitParams().size());
    Assert.assertEquals("hello", fakeMustInitParamsFilter.getInitParams().get("PARAM-1"));
    Assert.assertEquals("world", fakeMustInitParamsFilter.getInitParams().get("PARAM-2"));
  }
}
