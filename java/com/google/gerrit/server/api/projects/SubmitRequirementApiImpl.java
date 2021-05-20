package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.extensions.api.projects.SubmitRequirementApi;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.gerrit.server.restapi.project.CreateSubmitRequirement;
import com.google.gerrit.server.restapi.project.GetSubmitRequirement;
import com.google.gerrit.server.restapi.project.SubmitRequirementsCollection;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class SubmitRequirementApiImpl implements SubmitRequirementApi {
  interface Factory {
    SubmitRequirementApiImpl create(ProjectResource project, String name);
  }

  private final SubmitRequirementsCollection submitRequirements;
  private final CreateSubmitRequirement createSubmitRequirement;
  private final GetSubmitRequirement getSubmitRequirement;
  private final String name;
  private final ProjectCache projectCache;

  private ProjectResource project;

  @Inject
  SubmitRequirementApiImpl(
      SubmitRequirementsCollection submitRequirements,
      CreateSubmitRequirement createSubmitRequirement,
      GetSubmitRequirement getSubmitRequirement,
      ProjectCache projectCache,
      @Assisted ProjectResource project,
      @Assisted String name) {
    this.submitRequirements = submitRequirements;
    this.createSubmitRequirement = createSubmitRequirement;
    this.getSubmitRequirement = getSubmitRequirement;
    this.projectCache = projectCache;
    this.project = project;
    this.name = name;
  }

  @Override
  public SubmitRequirementApi create(SubmitRequirementInput input) throws RestApiException {
    try {
      createSubmitRequirement.apply(project, IdString.fromDecoded(name), input);

      // recreate project resource because project state was updated
      project =
          new ProjectResource(
              projectCache
                  .get(project.getNameKey())
                  .orElseThrow(illegalState(project.getNameKey())),
              project.getUser());

      return this;
    } catch (Exception e) {
      throw asRestApiException("Cannot create submit requirement", e);
    }
  }

  @Override
  public SubmitRequirementInfo get() throws RestApiException {
    try {
      return getSubmitRequirement.apply(resource()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get submit requirement", e);
    }
  }

  @Override
  public SubmitRequirementInfo update(SubmitRequirementInput input) throws RestApiException {
    /** TODO(ghareeb): implement */
    return null;
  }

  @Override
  public void delete() throws RestApiException {
    /** TODO(ghareeb): implement */
  }

  private SubmitRequirementResource resource() throws RestApiException, PermissionBackendException {
    return submitRequirements.parse(project, IdString.fromDecoded(name));
  }
}
