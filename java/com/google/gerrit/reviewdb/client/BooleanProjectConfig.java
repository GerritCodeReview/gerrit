package com.google.gerrit.reviewdb.client;

import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInfo.InheritedBooleanInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;

/**
 * Contains all inheritable boolean project configs and maps internal representations to API
 * objects.
 *
 * <p>Perform the following steps for adding a new inheritable boolean project config: 1) Add a
 * field to {@link ConfigInput} 2) Add a field to {@link ConfigInfo} 3) Add the config to the enum
 * here specifying Git config parameters and API mapper functions
 */
public enum BooleanProjectConfig {
  USE_CONTRIBUTOR_AGREEMENTS(
      "receive",
      "requireContributorAgreement",
      (i, v) -> i.useContributorAgreements = v,
      i -> i.useContributorAgreements),
  USE_SIGNED_OFF_BY(
      "receive", "requireSignedOffBy", (i, v) -> i.useSignedOffBy = v, i -> i.useSignedOffBy),
  USE_CONTENT_MERGE(
      "submit", "mergeContent", (i, v) -> i.useContentMerge = v, i -> i.useContentMerge),
  REQUIRE_CHANGE_ID(
      "receive", "requireChangeId", (i, v) -> i.requireChangeId = v, i -> i.requireChangeId),
  CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET(
      "receive",
      "createNewChangeForAllNotInTarget",
      (i, v) -> i.createNewChangeForAllNotInTarget = v,
      i -> i.createNewChangeForAllNotInTarget),
  ENABLE_SIGNED_PUSH(
      "receive", "enableSignedPush", (i, v) -> i.enableSignedPush = v, i -> i.enableSignedPush),
  REQUIRE_SIGNED_PUSH(
      "receive", "requireSignedPush", (i, v) -> i.requireSignedPush = v, i -> i.requireSignedPush),
  REJECT_IMPLICIT_MERGES(
      "receive",
      "rejectImplicitMerges",
      (i, v) -> i.rejectImplicitMerges = v,
      i -> i.rejectImplicitMerges),
  PRIVATE_BY_DEFAULT(
      "change", "privateByDefault", (i, v) -> i.privateByDefault = v, i -> i.privateByDefault),
  ENABLE_REVIEWER_BY_EMAIL(
      "reviewer",
      "enableByEmail",
      (i, v) -> i.enableReviewerByEmail = v,
      i -> i.enableReviewerByEmail),
  MATCH_AUTHOR_TO_COMMITTER_DATE(
      "project",
      "matchAuthorToCommitterDate",
      (i, v) -> i.matchAuthorToCommitterDate = v,
      i -> i.matchAuthorToCommitterDate);

  @FunctionalInterface
  private interface ToApi {
    void apply(ConfigInfo info, InheritedBooleanInfo val);
  }

  @FunctionalInterface
  private interface FromApi {
    InheritableBoolean apply(ConfigInput input);
  }

  // Git config
  private final String section;
  private final String name;

  // Api mapper functions
  private final ToApi toApi;
  private final FromApi fromApi;

  BooleanProjectConfig(String section, String name, ToApi toApi, FromApi fromApi) {
    this.section = section;
    this.name = name;
    this.toApi = toApi;
    this.fromApi = fromApi;
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

  public void set(ConfigInfo info, InheritedBooleanInfo val) {
    toApi.apply(info, val);
  }

  public InheritableBoolean get(ConfigInput input) {
    return fromApi.apply(input);
  }
}
