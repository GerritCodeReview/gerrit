package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectState;

import org.junit.Test;

public class CreateProjectIT extends AbstractDaemonTest {

  @Test
  public void withValidGroupName() throws Exception {
    final String newGroupName = "newGroup";
    adminSession.put("/groups/" + newGroupName);
    final String newProjectName = "validProject";
    sshSession.exec("gerrit create-project --branch master --owner "
        + newGroupName + " " + newProjectName);
    assert_().withFailureMessage(sshSession.getError())
        .that(sshSession.hasError()).isFalse();
    ProjectState projectState =
        projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNotNull();
  }

  @Test
  public void withInvalidGroupName() throws Exception {
    final String newGroupName = "newGroup";
    adminSession.put("/groups/" + newGroupName);
    final String wrongGroupName = "newG";
    final String newProjectName = "invalidProject";
    sshSession.exec("gerrit create-project --branch master --owner "
        + wrongGroupName + " " + newProjectName);
    assert_().withFailureMessage(sshSession.getError())
        .that(sshSession.hasError()).isTrue();
    ProjectState projectState =
        projectCache.get(new Project.NameKey(newProjectName));
    assertThat(projectState).isNull();
  }
}
