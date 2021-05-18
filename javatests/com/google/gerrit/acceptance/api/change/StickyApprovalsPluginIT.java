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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.ApprovalCopier;
import com.google.inject.AbstractModule;
import org.junit.Test;

/** Tests for the {@link ApprovalCopier} plugin interface. */
public class StickyApprovalsPluginIT extends AbstractDaemonTest {

  private static class AlwaysCopyPlugin extends AbstractModule {
    @Override
    protected void configure() {
      bind(ApprovalCopier.class)
          .annotatedWith(Exports.named("always-copy-plugin"))
          .toInstance((project, patchSetApproval, newPatchSet) -> true);
    }
  }

  private static class NeverCopyPlugin extends AbstractModule {
    @Override
    protected void configure() {
      bind(ApprovalCopier.class)
          .annotatedWith(Exports.named("never-copy-plugin"))
          .toInstance((project, patchSetApproval, newPatchSet) -> false);
    }
  }

  @Test
  public void approvalNotCopiedByDefault() throws Exception {
    try (AutoCloseable ignored = installPlugin("never-copy-plugin", NeverCopyPlugin.class)) {
      String change = createChange().getChangeId();
      approve(change);
      assertThat(gApi.changes().id(change).current().votes()).hasSize(1);
      amendChange(change);
      assertThat(gApi.changes().id(change).current().votes()).hasSize(0);
    }
  }

  @Test
  public void approvalCopiedIfPluginReturnsTrue() throws Exception {
    try (AutoCloseable ignored = installPlugin("never-copy-plugin", AlwaysCopyPlugin.class)) {
      String change = createChange().getChangeId();
      approve(change);
      assertThat(gApi.changes().id(change).current().votes()).hasSize(1);
      amendChange(change);
      assertThat(gApi.changes().id(change).current().votes()).hasSize(1);
    }
  }
}
