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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@NoHttpd
@UseSsh
@UseLocalDisk
public class ConvertRefStorageIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "true")
  public void convertsToReftableWhenPerRequestRefCacheEnabled() throws Exception {
    adminSshSession.exec(
        "gerrit convert-ref-storage --project " + project.get() + " --format reftable");
    adminSshSession.assertSuccess();
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(
              repo.getConfig()
                  .getString(
                      ConfigConstants.CONFIG_EXTENSIONS_SECTION,
                      null,
                      ConfigConstants.CONFIG_KEY_REF_STORAGE))
          .isEqualTo(ConfigConstants.CONFIG_REF_STORAGE_REFTABLE);
    }
  }
}
