// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.gerrit.testing.RefUpdateContextCollector;
import java.util.Map.Entry;
import org.junit.Rule;
import org.junit.Test;

public class DirectPushRefUpdateContextIT extends AbstractDaemonTest {
  @Rule
  public RefUpdateContextCollector refUpdateContextCollector = new RefUpdateContextCollector();

  @Test
  public void directPushWithoutJustification_emptyJustification() throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "change1", "a.txt", "content");

    PushOneCommit.Result result = push.to("refs/heads/master");

    result.assertOkStatus();
    ImmutableList<Entry<String, ImmutableList<RefUpdateContext>>> updates =
        refUpdateContextCollector.getContextsByUpdateType(RefUpdateType.DIRECT_PUSH);
    assertThat(updates).hasSize(1);
    Entry<String, ImmutableList<RefUpdateContext>> entry = updates.get(0);
    assertThat(entry.getKey()).isEqualTo("refs/heads/master");
    ImmutableList<RefUpdateContext> ctxts = entry.getValue();
    assertThat(ctxts).hasSize(1);
    RefUpdateContext ctx = ctxts.get(0);
    assertThat(ctx.getUpdateType()).isEqualTo(RefUpdateType.DIRECT_PUSH);
    assertThat(ctx.getJustification()).isEmpty();
  }

  @Test
  public void directPushWithJustification_justificationStoredInContext() throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "change1", "a.txt", "content");
    push.setPushOptions(ImmutableList.of("push-justification=test justification"));
    PushOneCommit.Result result = push.to("refs/heads/master");

    result.assertOkStatus();
    ImmutableList<Entry<String, ImmutableList<RefUpdateContext>>> updates =
        refUpdateContextCollector.getContextsByUpdateType(RefUpdateType.DIRECT_PUSH);
    assertThat(updates).hasSize(1);
    Entry<String, ImmutableList<RefUpdateContext>> entry = updates.get(0);
    assertThat(entry.getKey()).isEqualTo("refs/heads/master");
    ImmutableList<RefUpdateContext> ctxts = entry.getValue();
    assertThat(ctxts).hasSize(1);
    RefUpdateContext ctx = ctxts.get(0);
    assertThat(ctx.getUpdateType()).isEqualTo(RefUpdateType.DIRECT_PUSH);
    assertThat(ctx.getJustification()).hasValue("test justification");
  }
}
