// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class ContributorAgreementSerializer {
  public static ContributorAgreement deserialize(Cache.ContributorAgreementProto proto) {
    ContributorAgreement.Builder builder =
        ContributorAgreement.builder(proto.getName())
            .setDescription(emptyToNull(proto.getDescription()))
            .setAccepted(
                proto.getAcceptedList().stream()
                    .map(PermissionRuleSerializer::deserialize)
                    .collect(toImmutableList()))
            .setAgreementUrl(emptyToNull(proto.getUrl()))
            .setExcludeProjectsRegexes(proto.getExcludeRegularExpressionsList())
            .setMatchProjectsRegexes(proto.getMatchRegularExpressionsList());
    if (proto.hasAutoVerify()) {
      builder.setAutoVerify(GroupReferenceSerializer.deserialize(proto.getAutoVerify()));
    }
    return builder.build();
  }

  public static Cache.ContributorAgreementProto serialize(ContributorAgreement autoValue) {
    Cache.ContributorAgreementProto.Builder builder =
        Cache.ContributorAgreementProto.newBuilder()
            .setName(autoValue.getName())
            .setDescription(nullToEmpty(autoValue.getDescription()))
            .addAllAccepted(
                autoValue.getAccepted().stream()
                    .map(PermissionRuleSerializer::serialize)
                    .collect(toImmutableList()))
            .setUrl(nullToEmpty(autoValue.getAgreementUrl()))
            .addAllExcludeRegularExpressions(autoValue.getExcludeProjectsRegexes())
            .addAllMatchRegularExpressions(autoValue.getMatchProjectsRegexes());
    if (autoValue.getAutoVerify() != null) {
      builder.setAutoVerify(GroupReferenceSerializer.serialize(autoValue.getAutoVerify()));
    }
    return builder.build();
  }

  private ContributorAgreementSerializer() {}
}
