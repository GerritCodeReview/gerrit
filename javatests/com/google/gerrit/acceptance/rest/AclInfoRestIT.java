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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ApplyProvidedFixInput;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AclInfoRestIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeOperations changeOperations;

  @Test
  public void cannotApplyProvidedFixlWithoutAddPatchSetPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.ADD_PATCH_SET).ref("refs/*").group(ANONYMOUS_USERS))
        .update();

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .branch("master")
            .file("foo.txt")
            .content("some content")
            .create();

    Comment.Range range = new Comment.Range();
    range.startLine = 1;
    range.startCharacter = 0;
    range.endLine = 1;
    range.endCharacter = 3;

    FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
    fixReplacementInfo.path = "foo.txt";
    fixReplacementInfo.replacement = "other";
    fixReplacementInfo.range = range;

    List<FixReplacementInfo> fixReplacementInfoList = Arrays.asList(fixReplacementInfo);
    ApplyProvidedFixInput applyProvidedFixInput = new ApplyProvidedFixInput();
    applyProvidedFixInput.fixReplacementInfos = fixReplacementInfoList;

    // without VIEW_ACCESS capability no ACL info is returned
    RestResponse resp =
        userRestSession.post(
            "/changes/" + changeId + "/revisions/current/fix:apply", applyProvidedFixInput);
    resp.assertStatus(403);
    assertThat(resp.getEntityContent()).isEqualTo("edit not permitted");

    // with VIEW_ACCESS capability an ACL info is returned when the request fails due to a
    // permission error
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.VIEW_ACCESS).group(REGISTERED_USERS))
        .update();
    resp =
        userRestSession.post(
            "/changes/" + changeId + "/revisions/current/fix:apply", applyProvidedFixInput);
    resp.assertStatus(403);
    assertThat(resp.getEntityContent())
        .isEqualTo(
            String.format(
                "edit not permitted\n\n"
                    + "ACL info:\n"
                    + "* '%s' can perform 'read' with force=false on project '%s' for ref"
                    + " 'refs/heads/master' (allowed for group 'global:Anonymous-Users' by rule"
                    + " 'group Anonymous Users')\n"
                    + "* '%s' can perform 'push' with force=false on project '%s' for ref"
                    + " 'refs/for/refs/heads/master' (allowed for group 'global:Registered-Users'"
                    + " by rule 'group Registered Users')\n"
                    + "* '%s' cannot perform 'addPatchSet' with force=false on project '%s' for ref"
                    + " 'refs/for/refs/heads/master' because this permission is blocked",
                user.username(), project, user.username(), project, user.username(), project));

    // with VIEW_ACCESS capability no ACL info is returned when the request doesn't fail due to a
    // permission error
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.ADD_PATCH_SET).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
    resp =
        userRestSession.post(
            "/changes/" + changeId + "/revisions/current/fix:apply", applyProvidedFixInput);
    resp.assertOK();
    assertThat(resp.getEntityContent()).doesNotContain("ACL info");
  }
}
