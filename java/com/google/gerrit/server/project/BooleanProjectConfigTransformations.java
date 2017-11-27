package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInfo.InheritedBooleanInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import java.util.Arrays;
import java.util.HashSet;

public class BooleanProjectConfigTransformations {

  private static ImmutableMap<BooleanProjectConfig, ToApi> TO_API;
  private static ImmutableMap<BooleanProjectConfig, FromApi> FROM_API;

  static {
    ImmutableMap.Builder<BooleanProjectConfig, ToApi> toApiBuilder = ImmutableMap.builder();
    toApiBuilder
        .put(
            BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS,
            (i, v) -> i.useContributorAgreements = v)
        .put(BooleanProjectConfig.USE_SIGNED_OFF_BY, (i, v) -> i.useSignedOffBy = v)
        .put(BooleanProjectConfig.USE_CONTENT_MERGE, (i, v) -> i.useContentMerge = v)
        .put(BooleanProjectConfig.REQUIRE_CHANGE_ID, (i, v) -> i.requireChangeId = v)
        .put(
            BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
            (i, v) -> i.createNewChangeForAllNotInTarget = v)
        .put(BooleanProjectConfig.ENABLE_SIGNED_PUSH, (i, v) -> i.enableSignedPush = v)
        .put(BooleanProjectConfig.REQUIRE_SIGNED_PUSH, (i, v) -> i.requireSignedPush = v)
        .put(BooleanProjectConfig.REJECT_IMPLICIT_MERGES, (i, v) -> i.rejectImplicitMerges = v)
        .put(BooleanProjectConfig.PRIVATE_BY_DEFAULT, (i, v) -> i.privateByDefault = v)
        .put(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL, (i, v) -> i.enableReviewerByEmail = v)
        .put(
            BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE,
            (i, v) -> i.matchAuthorToCommitterDate = v);
    TO_API = toApiBuilder.build();

    ImmutableMap.Builder<BooleanProjectConfig, FromApi> fromApiBuilder = ImmutableMap.builder();
    fromApiBuilder
        .put(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, i -> i.useContributorAgreements)
        .put(BooleanProjectConfig.USE_SIGNED_OFF_BY, i -> i.useSignedOffBy)
        .put(BooleanProjectConfig.USE_CONTENT_MERGE, i -> i.useContentMerge)
        .put(BooleanProjectConfig.REQUIRE_CHANGE_ID, i -> i.requireChangeId)
        .put(
            BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
            i -> i.createNewChangeForAllNotInTarget)
        .put(BooleanProjectConfig.ENABLE_SIGNED_PUSH, i -> i.enableSignedPush)
        .put(BooleanProjectConfig.REQUIRE_SIGNED_PUSH, i -> i.requireSignedPush)
        .put(BooleanProjectConfig.REJECT_IMPLICIT_MERGES, i -> i.rejectImplicitMerges)
        .put(BooleanProjectConfig.PRIVATE_BY_DEFAULT, i -> i.privateByDefault)
        .put(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL, i -> i.enableReviewerByEmail)
        .put(
            BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE, i -> i.matchAuthorToCommitterDate);
    FROM_API = fromApiBuilder.build();

    // Verify that each BooleanProjectConfig has to/from API mappers in BooleanProjectConfigTransformations
    if (!Sets.symmetricDifference(TO_API.keySet(), FROM_API.keySet()).isEmpty()) {
      throw new IllegalStateException(
          "BooleanProjectConfigTransformations for converting to/from API must have the same entries");
    }
    if (!Sets.symmetricDifference(
            TO_API.keySet(), new HashSet<>(Arrays.asList(BooleanProjectConfig.values())))
        .isEmpty()) {
      throw new IllegalStateException(
          "All values of BooleanProjectConfig must have transformations associated with them");
    }
  }

  @FunctionalInterface
  private interface ToApi {
    void apply(ConfigInfo info, InheritedBooleanInfo val);
  }

  @FunctionalInterface
  private interface FromApi {
    InheritableBoolean apply(ConfigInput input);
  }

  public static void set(BooleanProjectConfig cfg, ConfigInfo info, InheritedBooleanInfo val) {
    TO_API.get(cfg).apply(info, val);
  }

  public static InheritableBoolean get(BooleanProjectConfig cfg, ConfigInput input) {
    return FROM_API.get(cfg).apply(input);
  }
}
