package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.projects.DeleteChangesInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.Response;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
public class DeleteChangesIT extends AbstractDaemonTest {

  private ProjectApi project() throws Exception {
    return gApi.projects().name(project.get());
  }

  @Test
  public void deleteChangesFailure() throws Exception {
    DeleteChangesInput deleteInput = new DeleteChangesInput();
    deleteInput.changes = List.of("Non-existing-change-1", "Non-existing-change-1");

    Response<?> response = project().deleteChanges(deleteInput);
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.value().toString())
        .isEqualTo("{FAILURE=[[Non-existing-change-1], [Non-existing-change-1]]}");
  }

  @Test
  public void deleteChangesSuccess() throws Exception {
    PushOneCommit.Result c1 = createChange();
    PushOneCommit.Result c2 = createChange();
    assertThat(c1.getChange().change().getProject().get()).isNotNull();
    assertThat(c2.getChange().change().getProject().get()).isNotNull();
    DeleteChangesInput deleteInput = new DeleteChangesInput();
    deleteInput.changes = List.of(c1.getChangeId(), c2.getChangeId());
    Response<?> response = project().deleteChanges(deleteInput);
    assertThat(response.value().toString().contains("SUCCESS")).isTrue();
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void deleteChangesSuccessFailureNotUnique() throws Exception {
    TestRepository<InMemoryRepository> repo = cloneProject(project);
    PushOneCommit.Result c1 = createChange(repo, "master", "Add a file", "foo", "content", null);
    PushOneCommit.Result c2 = createChange();

    // cherry pick a change to make a duplicate change for NOT_UNIQUE case.
    String newBranch = "Test-branch";
    createBranch(BranchNameKey.create(project, newBranch));
    CherryPickInput cpi = new CherryPickInput();
    cpi.destination = newBranch;
    gApi.changes().id(c1.getChangeId()).current().cherryPick(cpi);

    DeleteChangesInput deleteInput = new DeleteChangesInput();
    deleteInput.changes = List.of(c1.getChangeId(), c2.getChangeId(), "Non-existing-change-1");
    Response<?> response = project().deleteChanges(deleteInput);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.value().toString()).contains("SUCCESS");
    assertThat(response.value().toString()).contains("NOT_UNIQUE");
    assertThat(response.value().toString()).contains("FAILURE");
  }
}
