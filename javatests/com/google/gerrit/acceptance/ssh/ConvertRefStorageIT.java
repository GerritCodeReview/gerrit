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

package com.google.gerrit.acceptance.ssh;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import org.junit.Test;

@NoHttpd
@UseSsh
public class ConvertRefStorageIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "true")
  public void refusesConversionToReftableWhenPerRequestRefCacheEnabled() throws Exception {
    adminSshSession.exec(
        "gerrit convert-ref-storage --project " + project.get() + " --format reftable");
    adminSshSession.assertFailure(
        "Converting to reftable requires core.usePerRequestRefCache=false");
  }
}
