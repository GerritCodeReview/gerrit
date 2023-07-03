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

package com.google.gerrit.server.update.context;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.DIRECT_PUSH;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.GROUPS_UPDATE;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.INIT_REPO;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.OTHER;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.After;
import org.junit.Test;

public class RefUpdateContextTest {
  @After
  public void tearDown() {
    // Each test should close all opened context to avoid interference with other tests.
    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
  }

  @Test
  public void contextNotOpen() {
    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
    assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
    assertThat(RefUpdateContext.hasOpen(GROUPS_UPDATE)).isFalse();
  }

  @Test
  public void singleContext_openedAndClosedCorrectly() {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      ImmutableList<RefUpdateContext> openedContexts = RefUpdateContext.getOpenedContexts();
      assertThat(openedContexts).hasSize(1);
      assertThat(openedContexts.get(0).getUpdateType()).isEqualTo(CHANGE_MODIFICATION);
      assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isTrue();
      assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
    }

    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
    assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isFalse();
  }

  @Test
  public void nestedContext_openedAndClosedCorrectly() {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (RefUpdateContext nestedCtx = RefUpdateContext.open(INIT_REPO)) {
        ImmutableList<RefUpdateContext> nestedOpenedContexts = RefUpdateContext.getOpenedContexts();
        assertThat(nestedOpenedContexts).hasSize(2);
        assertThat(nestedOpenedContexts.get(0).getUpdateType()).isEqualTo(CHANGE_MODIFICATION);
        assertThat(nestedOpenedContexts.get(1).getUpdateType()).isEqualTo(INIT_REPO);
        assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isTrue();
        assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isTrue();
        assertThat(RefUpdateContext.hasOpen(GROUPS_UPDATE)).isFalse();
      }
      ImmutableList<RefUpdateContext> openedContexts = RefUpdateContext.getOpenedContexts();
      assertThat(openedContexts).hasSize(1);
      assertThat(openedContexts.get(0).getUpdateType()).isEqualTo(CHANGE_MODIFICATION);
      assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isTrue();
      assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
      assertThat(RefUpdateContext.hasOpen(GROUPS_UPDATE)).isFalse();
    }

    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
    assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isFalse();
    assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
    assertThat(RefUpdateContext.hasOpen(GROUPS_UPDATE)).isFalse();
  }

  @Test
  public void incorrectCloseOrder_exceptionThrown() {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (RefUpdateContext nestedCtx = RefUpdateContext.open(INIT_REPO)) {
        assertThrows(Exception.class, () -> ctx.close());
        ImmutableList<RefUpdateContext> openedContexts = RefUpdateContext.getOpenedContexts();
        assertThat(openedContexts).hasSize(2);
        assertThat(RefUpdateContext.hasOpen(CHANGE_MODIFICATION)).isTrue();
        assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isTrue();
      }
    }
  }

  @Test
  public void openDirectPushContextByType_exceptionThrown() {
    assertThrows(Exception.class, () -> RefUpdateContext.open(DIRECT_PUSH));
  }

  @Test
  public void openDirectPushContextWithJustification_openedAndClosedCorrectly() {
    try (RefUpdateContext ctx = RefUpdateContext.openDirectPush(Optional.of("Open in test"))) {
      ImmutableList<RefUpdateContext> openedContexts = RefUpdateContext.getOpenedContexts();
      assertThat(openedContexts).hasSize(1);
      assertThat(openedContexts.get(0).getUpdateType()).isEqualTo(DIRECT_PUSH);
      assertThat(openedContexts.get(0).getJustification()).hasValue("Open in test");
      assertThat(RefUpdateContext.hasOpen(DIRECT_PUSH)).isTrue();
      assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
    }

    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
    assertThat(RefUpdateContext.hasOpen(DIRECT_PUSH)).isFalse();
  }

  @Test
  public void openDirectPushContextWithoutJustification_openedAndClosedCorrectly() {
    try (RefUpdateContext ctx = RefUpdateContext.openDirectPush(Optional.empty())) {
      ImmutableList<RefUpdateContext> openedContexts = RefUpdateContext.getOpenedContexts();
      assertThat(openedContexts).hasSize(1);
      assertThat(openedContexts.get(0).getUpdateType()).isEqualTo(DIRECT_PUSH);
      assertThat(openedContexts.get(0).getJustification()).isEmpty();
      assertThat(RefUpdateContext.hasOpen(DIRECT_PUSH)).isTrue();
      assertThat(RefUpdateContext.hasOpen(INIT_REPO)).isFalse();
    }

    assertThat(RefUpdateContext.getOpenedContexts()).isEmpty();
    assertThat(RefUpdateContext.hasOpen(DIRECT_PUSH)).isFalse();
  }

  @Test
  public void openOtherContextByType_exceptionThrown() {
    assertThrows(Exception.class, () -> RefUpdateContext.open(OTHER));
  }
}
