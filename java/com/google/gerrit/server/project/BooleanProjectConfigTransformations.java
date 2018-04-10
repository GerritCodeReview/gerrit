// Copyright (C) 2017 The Android Open Source Project
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

/** Provides transformations to get and set BooleanProjectConfigs from the API. */
public class BooleanProjectConfigTransformations {

  private static ImmutableMap<BooleanProjectConfig, Mapper> MAPPER =
      ImmutableMap.<BooleanProjectConfig, Mapper>builder()
          .put(
              BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS,
              new Mapper(i -> i.useContributorAgreements, (i, v) -> i.useContributorAgreements = v))
          .put(
              BooleanProjectConfig.USE_SIGNED_OFF_BY,
              new Mapper(i -> i.useSignedOffBy, (i, v) -> i.useSignedOffBy = v))
          .put(
              BooleanProjectConfig.USE_CONTENT_MERGE,
              new Mapper(i -> i.useContentMerge, (i, v) -> i.useContentMerge = v))
          .put(
              BooleanProjectConfig.REQUIRE_CHANGE_ID,
              new Mapper(i -> i.requireChangeId, (i, v) -> i.requireChangeId = v))
          .put(
              BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
              new Mapper(
                  i -> i.createNewChangeForAllNotInTarget,
                  (i, v) -> i.createNewChangeForAllNotInTarget = v))
          .put(
              BooleanProjectConfig.ENABLE_SIGNED_PUSH,
              new Mapper(i -> i.enableSignedPush, (i, v) -> i.enableSignedPush = v))
          .put(
              BooleanProjectConfig.REQUIRE_SIGNED_PUSH,
              new Mapper(i -> i.requireSignedPush, (i, v) -> i.requireSignedPush = v))
          .put(
              BooleanProjectConfig.REJECT_IMPLICIT_MERGES,
              new Mapper(i -> i.rejectImplicitMerges, (i, v) -> i.rejectImplicitMerges = v))
          .put(
              BooleanProjectConfig.PRIVATE_BY_DEFAULT,
              new Mapper(i -> i.privateByDefault, (i, v) -> i.privateByDefault = v))
          .put(
              BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL,
              new Mapper(i -> i.enableReviewerByEmail, (i, v) -> i.enableReviewerByEmail = v))
          .put(
              BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE,
              new Mapper(
                  i -> i.matchAuthorToCommitterDate, (i, v) -> i.matchAuthorToCommitterDate = v))
          .put(
              BooleanProjectConfig.REJECT_EMPTY_COMMIT,
              new Mapper(i -> i.rejectEmptyCommit, (i, v) -> i.rejectEmptyCommit = v))
          .build();

  static {
    // Verify that each BooleanProjectConfig has to/from API mappers in
    // BooleanProjectConfigTransformations
    if (!Sets.symmetricDifference(
            MAPPER.keySet(), new HashSet<>(Arrays.asList(BooleanProjectConfig.values())))
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
    MAPPER.get(cfg).set(info, val);
  }

  public static InheritableBoolean get(BooleanProjectConfig cfg, ConfigInput input) {
    return MAPPER.get(cfg).get(input);
  }

  private static class Mapper {
    private final FromApi fromApi;
    private final ToApi toApi;

    private Mapper(FromApi fromApi, ToApi toApi) {
      this.fromApi = fromApi;
      this.toApi = toApi;
    }

    public void set(ConfigInfo info, InheritedBooleanInfo val) {
      toApi.apply(info, val);
    }

    public InheritableBoolean get(ConfigInput input) {
      return fromApi.apply(input);
    }
  }
}
