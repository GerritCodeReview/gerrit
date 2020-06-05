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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.NotifyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ProjectConfigUpdate {
  final List<UpdateAccessSections> accessSections;
  final List<Function<Map<String, ContributorAgreement>, Map<String, ContributorAgreement>>>
      contributorAgreements;
  final List<Function<Map<String, CommentLinkInfoImpl>, Map<String, CommentLinkInfoImpl>>>
      commentLinkSections;
  final List<
          Function<Map<Project.NameKey, SubscribeSection>, Map<Project.NameKey, SubscribeSection>>>
      subscribeSections;
  final List<Function<Map<String, NotifyConfig>, Map<String, NotifyConfig>>> notifySections;

  ProjectConfigUpdate() {
    accessSections = new ArrayList<>();
    contributorAgreements = new ArrayList<>();
    commentLinkSections = new ArrayList<>();
    subscribeSections = new ArrayList<>();
    notifySections = new ArrayList<>();
  }

  @FunctionalInterface
  public interface UpdateAccessSections {
    Map<String, AccessSection> update(
        Map<String, AccessSection> accessSections, Set<String> sectionsWithUnknownPermissions);
  }

  public void updateAccessSections(UpdateAccessSections accessSections) {
    this.accessSections.add(accessSections);
  }

  public void updateContributorAgreements(
      Function<Map<String, ContributorAgreement>, Map<String, ContributorAgreement>>
          contributorAgreements) {
    this.contributorAgreements.add(contributorAgreements);
  }

  public void updateCommentLinkSections(
      Function<Map<String, CommentLinkInfoImpl>, Map<String, CommentLinkInfoImpl>>
          commentLinkSections) {
    this.commentLinkSections.add(commentLinkSections);
  }

  public void updateSubscribeSection(
      Function<Map<Project.NameKey, SubscribeSection>, Map<Project.NameKey, SubscribeSection>>
          subscribeSection) {
    this.subscribeSections.add(subscribeSection);
  }

  public void updateNotifyConfig(
      Function<Map<String, NotifyConfig>, Map<String, NotifyConfig>> notifyConfig) {
    this.notifySections.add(notifyConfig);
  }

  <T> T update(T beforeUpdate, List<Function<T, T>> updates) {
    T result = beforeUpdate;
    for (Function<T, T> updateFunction : updates) {
      result = updateFunction.apply(result);
    }
    return result;
  }
}
