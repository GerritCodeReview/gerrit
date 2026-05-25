// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;

/** Reruns {@link RestApiServletIT} with the Jetty thread pool routed through the WorkQueue. */
public class RestApiServletWithWorkQueueIT extends RestApiServletIT {
  @ConfigSuite.Default
  public static Config useWorkQueue() {
    Config cfg = new Config();
    cfg.setBoolean("httpd", null, "useWorkQueue", true);
    return cfg;
  }
}
