// Copyright (C) 2024 The Android Open Source Project
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
import static com.google.gerrit.acceptance.TestExtensions.TestCommitValidationInfoListener;
import static com.google.gerrit.acceptance.TestExtensions.TestCommitValidationListener;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.server.git.validators.CommitValidationInfo;
import com.google.inject.Inject;
import org.junit.Test;

public class DirectPushIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void commitValidationInfoListenerIsInvokedOnDirectPush() throws Exception {
    TestCommitValidationInfoListener testCommitValidationInfoListener =
        new TestCommitValidationInfoListener();
    TestCommitValidationListener testCommitValidationListener = new TestCommitValidationListener();
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(testCommitValidationInfoListener)
            .add(testCommitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("skip-foo=true"));
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();

      assertThat(testCommitValidationInfoListener.validationInfoByValidator)
          .containsKey(TestCommitValidationListener.class.getName());
      assertThat(
              testCommitValidationInfoListener
                  .validationInfoByValidator
                  .get(TestCommitValidationListener.class.getName())
                  .status())
          .isEqualTo(CommitValidationInfo.Status.PASSED);
      assertThat(testCommitValidationInfoListener.receiveEvent.commit.name())
          .isEqualTo(r.getCommit().name());
      assertThat(testCommitValidationInfoListener.receiveEvent.pushOptions)
          .containsExactly("skip-foo", "true");
      assertThat(testCommitValidationInfoListener.patchSetId).isNull();
      assertThat(testCommitValidationInfoListener.hasChangeModificationRefContext).isFalse();
      assertThat(testCommitValidationInfoListener.hasDirectPushRefContext).isTrue();
    }
  }

  @Test
  public void commitValidationInfoListenerIsInvokedOnDirectPush_skipValidation() throws Exception {
    // Using "o=skip-validation" requires the user to have 'Forge Author', 'Forge Committer', 'Forge
    // Server' and 'Push Merge' permissions.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.FORGE_AUTHOR).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.FORGE_COMMITTER).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.FORGE_SERVER).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH_MERGE).ref("refs/*").group(adminGroupUuid()))
        .update();

    TestCommitValidationInfoListener testCommitValidationInfoListener =
        new TestCommitValidationInfoListener();
    TestCommitValidationListener testCommitValidationListener = new TestCommitValidationListener();
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(testCommitValidationInfoListener)
            .add(testCommitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("skip-validation"));
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();

      assertThat(testCommitValidationInfoListener.validationInfoByValidator)
          .containsKey(TestCommitValidationListener.class.getName());
      assertThat(
              testCommitValidationInfoListener
                  .validationInfoByValidator
                  .get(TestCommitValidationListener.class.getName())
                  .status())
          .isEqualTo(CommitValidationInfo.Status.SKIPPED_BY_USER);
      assertThat(testCommitValidationInfoListener.receiveEvent.commit.name())
          .isEqualTo(r.getCommit().name());
      assertThat(testCommitValidationInfoListener.receiveEvent.pushOptions)
          .containsExactly("skip-validation", "");
      assertThat(testCommitValidationInfoListener.patchSetId).isNull();
      assertThat(testCommitValidationInfoListener.hasChangeModificationRefContext).isFalse();
      assertThat(testCommitValidationInfoListener.hasDirectPushRefContext).isTrue();
    }
  }
}
