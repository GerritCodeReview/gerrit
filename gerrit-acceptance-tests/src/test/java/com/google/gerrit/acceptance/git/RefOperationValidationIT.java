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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

public class RefOperationValidationIT extends AbstractDaemonTest {
  private static final String TEST_REF = "refs/heads/protected";

  @Inject DynamicSet<RefOperationValidationListener> validators;

  private class TestRefValidator implements RefOperationValidationListener, AutoCloseable {
    private final ReceiveCommand.Type rejectType;
    private final RegistrationHandle handle;

    public TestRefValidator(ReceiveCommand.Type rejectType) {
      this.rejectType = rejectType;
      this.handle = validators.add(this);
    }

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
        throws ValidationException {
      if (refEvent.command.getType().equals(rejectType)) {
        throw new ValidationException(rejectType.name());
      }
      return Collections.emptyList();
    }

    @Override
    public void close() throws Exception {
      handle.remove();
    }
  }

  @Test
  public void rejectRefCreation() throws Exception {
    try (TestRefValidator validator = new TestRefValidator(ReceiveCommand.Type.CREATE)) {
      gApi.projects().name(project.get()).branch(TEST_REF).create(new BranchInput());
      assert_().fail("expected exception");
    } catch (RestApiException expected) {
      assertThat(expected).hasMessageThat().contains(ReceiveCommand.Type.CREATE.name());
    }
  }

  @Test
  public void rejectRefDeletion() throws Exception {
    gApi.projects().name(project.get()).branch(TEST_REF).create(new BranchInput());
    try (TestRefValidator validator = new TestRefValidator(ReceiveCommand.Type.DELETE)) {
      gApi.projects().name(project.get()).branch(TEST_REF).delete();
      assert_().fail("expected exception");
    } catch (RestApiException expected) {
      assertThat(expected).hasMessageThat().contains(ReceiveCommand.Type.DELETE.name());
    }
  }

  @Test
  public void rejectRefUpdateFastForward() throws Exception {
    try (TestRefValidator validator = new TestRefValidator(ReceiveCommand.Type.UPDATE)) {
      grant(project, "refs/*", Permission.PUSH, true);
      PushOneCommit push1 =
          pushFactory.create(db, admin.getIdent(), testRepo, "change1", "a.txt", "content");
      PushOneCommit.Result r1 = push1.to("refs/heads/master");
      r1.assertErrorStatus(ReceiveCommand.Type.UPDATE.name());
    }
  }

  @Test
  public void rejectRefUpdateNonFastForward() throws Exception {
    try (TestRefValidator validator =
        new TestRefValidator(ReceiveCommand.Type.UPDATE_NONFASTFORWARD)) {
      ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
      grant(project, "refs/*", Permission.PUSH, true);
      PushOneCommit push1 =
          pushFactory.create(db, admin.getIdent(), testRepo, "change1", "a.txt", "content");
      PushOneCommit.Result r1 = push1.to("refs/heads/master");
      r1.assertOkStatus();

      // Reset HEAD to initial so the new change is a non-fast forward
      RefUpdate ru = repo().updateRef(HEAD);
      ru.setNewObjectId(initial);
      assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

      PushOneCommit push2 =
          pushFactory.create(db, admin.getIdent(), testRepo, "change2", "b.txt", "content");
      push2.setForce(true);
      PushOneCommit.Result r2 = push2.to("refs/heads/master");
      r2.assertErrorStatus(ReceiveCommand.Type.UPDATE_NONFASTFORWARD.name());
    }
  }
}
