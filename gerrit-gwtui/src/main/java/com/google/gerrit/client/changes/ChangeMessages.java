// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gwt.i18n.client.Messages;

public interface ChangeMessages extends Messages {
  String accountDashboardTitle(String fullName);
  String changesOpenInProject(String string);
  String changesMergedInProject(String string);
  String changesAbandonedInProject(String string);

  String revertChangeDefaultMessage(String commitMsg, String commitId);

  String changeScreenTitleId(String changeId);
  String outdatedHeader(int outdated);
  String patchSetHeader(int id);
  String loadingPatchSet(int id);
  String submitPatchSet(int id);

  String patchTableComments(@PluralCount int count);
  String patchTableDrafts(@PluralCount int count);
  String patchTableSize_Modify(int insertions, int deletions);
  String patchTableSize_Lines(@PluralCount int insertions);

  String removeReviewer(String fullName);
  String messageWrittenOn(String date);

  String renamedFrom(String sourcePath);
  String copiedFrom(String sourcePath);
  String otherFrom(String sourcePath);

  String needApproval(String labelName);
  String publishComments(String changeId, int ps);
  String lineHeader(int line);

  String changeQueryWindowTitle(String query);
  String changeQueryPageTitle(String query);

  String reviewerNotFound(String who);
  String accountInactive(String who);
  String changeNotVisibleTo(String who);
  String groupIsEmpty(String group);
  String groupIsNotAllowed(String group);
  String groupHasTooManyMembers(String group);
  String groupManyMembersConfirmation(String group, int memberCount);

  String secondsAgo(long seconds);
  String minutesAgo(long minutes);
  String hoursAgo(long hours);
  String daysAgo(long days);
  String weeksAgo(long weeks);
  String monthsAgo(long months);
  String yearsAgo(long years);
  String years0MonthsAgo(long years, String yearLabel);
  String yearsMonthsAgo(long years, String yearLabel, long months,
      String monthLabel);
}
