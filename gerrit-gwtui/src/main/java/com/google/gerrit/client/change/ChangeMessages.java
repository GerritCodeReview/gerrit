// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gwt.i18n.client.Messages;

public interface ChangeMessages extends Messages {
  String patchSets(String currentlyViewedPatchSet, int currentPatchSet);

  String changeWithNoRevisions(int changeId);

  String relatedChanges(int count);

  String relatedChanges(String count);

  String conflictingChanges(int count);

  String conflictingChanges(String count);

  String cherryPicks(int count);

  String cherryPicks(String count);

  String sameTopic(int count);

  String sameTopic(String count);

  String submittedTogether(int count);

  String submittedTogether(String count);

  String editPatchSet(int patchSet);

  String failedToLoadFileList(String error);
}
