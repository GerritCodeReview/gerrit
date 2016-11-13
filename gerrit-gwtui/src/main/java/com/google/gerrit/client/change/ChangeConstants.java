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

import com.google.gwt.i18n.client.Constants;

public interface ChangeConstants extends Constants {
  String previousChange();

  String nextChange();

  String openChange();

  String reviewedFileTitle();

  String editFileInline();

  String removeFileInline();

  String restoreFileInline();

  String openLastFile();

  String openCommitMessage();

  String patchSet();

  String commit();

  String date();

  String author();

  String draft();

  String notAvailable();

  String relatedChanges();

  String relatedChangesTooltip();

  String conflictingChanges();

  String conflictingChangesTooltip();

  String cherryPicks();

  String cherryPicksTooltip();

  String sameTopic();

  String sameTopicTooltip();

  String submittedTogether();

  String submittedTogetherTooltip();

  String noChanges();

  String indirectAncestor();

  String merged();

  String abandoned();

  String deleteChangeEdit();

  String deleteChange();

  String deleteDraftRevision();
}
