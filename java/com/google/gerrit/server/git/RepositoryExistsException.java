package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import java.io.IOException;

/** Thrown when trying to create a repository that exist. */
public class RepositoryExistsException extends IOException {
  private static final long serialVersionUID = 1L;

  /**
   * @param projectName name of the project that cannot be created
   * @param reason reason why the project cannot be created
   */
  public RepositoryExistsException(Project.NameKey projectName, String reason) {
    super(
        String.format("Repository %s exists and cannot be created. %s", projectName.get(), reason));
  }

  /** @param projectName name of the project that cannot be created */
  public RepositoryExistsException(Project.NameKey projectName) {
    super(String.format("Repository %s exists and cannot be created.", projectName.get()));
  }
}
