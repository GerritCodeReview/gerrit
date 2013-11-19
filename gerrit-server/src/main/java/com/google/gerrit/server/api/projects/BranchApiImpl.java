package com.google.gerrit.server.api.projects;

import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.CreateBranch;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

public class BranchApiImpl implements BranchApi {
  interface Factory {
    BranchApiImpl create(ProjectResource project, String ref);
  }

  private final CreateBranch.Factory createBranchFactory;
  private final String ref;
  private final ProjectResource project;

  @Inject
  BranchApiImpl(
      CreateBranch.Factory createBranchFactory,
      @Assisted ProjectResource project,
      @Assisted String ref) {
    this.createBranchFactory = createBranchFactory;
    this.project = project;
    this.ref = ref;
  }

  @Override
  public BranchApi create(BranchInput in) throws RestApiException {
    try {
      CreateBranch.Input input = new CreateBranch.Input();
      input.ref = ref;
      input.revision = in.revision;
      createBranchFactory.create(ref).apply(project, input);
      return this;
    } catch (IOException e) {
      throw new RestApiException("Cannot create branch", e);
    }
  }
}
