package com.google.gerrit.reviewdb.client;

/**
 * Contains all inheritable boolean project configs and maps internal representations to API
 * objects.
 *
 * <p>Perform the following steps for adding a new inheritable boolean project config:
 *
 * <ol>
 *   <li>Add a field to {@link com.google.gerrit.extensions.api.projects.ConfigInput}
 *   <li>Add a field to {@link com.google.gerrit.extensions.api.projects.ConfigInfo}
 *   <li>Add the config to this enum
 *   <li>Add API mappers to {@link
 *       com.google.gerrit.server.project.BooleanProjectConfigTransformations}
 * </ol>
 */
public enum BooleanProjectConfig {
  USE_CONTRIBUTOR_AGREEMENTS("receive", "requireContributorAgreement"),
  USE_SIGNED_OFF_BY("receive", "requireSignedOffBy"),
  USE_CONTENT_MERGE("submit", "mergeContent"),
  REQUIRE_CHANGE_ID("receive", "requireChangeId"),
  CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET("receive", "createNewChangeForAllNotInTarget"),
  ENABLE_SIGNED_PUSH("receive", "enableSignedPush"),
  REQUIRE_SIGNED_PUSH("receive", "requireSignedPush"),
  REJECT_IMPLICIT_MERGES("receive", "rejectImplicitMerges"),
  PRIVATE_BY_DEFAULT("change", "privateByDefault"),
  ENABLE_REVIEWER_BY_EMAIL("reviewer", "enableByEmail"),
  MATCH_AUTHOR_TO_COMMITTER_DATE("project", "matchAuthorToCommitterDate");

  // Git config
  private final String section;
  private final String name;

  BooleanProjectConfig(String section, String name) {
    this.section = section;
    this.name = name;
  }

  public String getSection() {
    return section;
  }

  public String getSubSection() {
    return null;
  }

  public String getName() {
    return name;
  }
}
