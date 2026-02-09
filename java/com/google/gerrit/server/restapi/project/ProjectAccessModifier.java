package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.exceptions.InvalidNameException;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * Helper to modify the project config of a project.
 *
 * <p>This class processes a {@link ProjectAccessInput} to update the access sections of a project.
 * It handles the validation of the input, resolution of access sections, and application of
 * additions and removals of permissions. The changes are committed using the provided {@link
 * RepoMetaDataUpdater}.
 */
@Singleton
public class ProjectAccessModifier {
  private final SetAccessUtil setAccessUtil;

  @Inject
  ProjectAccessModifier(SetAccessUtil setAccessUtil) {
    this.setAccessUtil = setAccessUtil;
  }

  public ImmutableList<AccessSection> getAccessSections(
      Map<String, AccessSectionInfo> sectionInfos, boolean rejectNonResolvableGroups)
      throws UnprocessableEntityException {
    return setAccessUtil.getAccessSections(sectionInfos, rejectNonResolvableGroups);
  }

  public void validateChanges(
      ProjectConfig config, List<AccessSection> removals, List<AccessSection> additions)
      throws BadRequestException, InvalidNameException {
    setAccessUtil.validateChanges(config, removals, additions);
  }

  public void applyChanges(
      ProjectConfig config, List<AccessSection> removals, List<AccessSection> additions) {
    setAccessUtil.applyChanges(config, removals, additions);
  }
}
