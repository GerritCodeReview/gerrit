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

  String revertChangeDefaultMessage(String commitMsg, String commitId);

  String cherryPickedChangeDefaultMessage(String commitMsg, String commitId);

  String changeScreenTitleId(String changeId);

  String loadingPatchSet(int id);

  String patchTableSize_Modify(int insertions, int deletions);

  String patchTableSize_ModifyBinaryFiles(String bytesInserted, String bytesDeleted);

  String patchTableSize_ModifyBinaryFilesWithPercentages(
      String bytesInserted,
      String percentageInserted,
      String bytesDeleted,
      String percentageDeleted);

  String patchTableSize_LongModify(int insertions, int deletions);

  String removeReviewer(String fullName);

  String removeVote(String label);

  String blockedOn(String labelName);

  String needs(String labelName);

  String changeQueryWindowTitle(String query);

  String changeQueryPageTitle(String query);

  String insertionsAndDeletions(int insertions, int deletions);

  String diffBaseParent(int parentNum);
}
