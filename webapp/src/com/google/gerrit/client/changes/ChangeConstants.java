// Copyright 2008 Google Inc.
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

import com.google.gwt.i18n.client.Constants;

public interface ChangeConstants extends Constants {
  String statusLongNew();
  String statusLongMerged();
  String statusLongAbandoned();

  String changesRecentlyClosed();

  String starredHeading();

  String changeTableColumnID();
  String changeTableColumnSubject();
  String changeTableColumnOwner();
  String changeTableColumnReviewers();
  String changeTableColumnProject();
  String changeTableColumnLastUpdate();
  String changeTableNone();

  String changeScreenDescription();
  String changeScreenDependencies();
  String changeScreenDependsOn();
  String changeScreenNeededBy();
  String changeScreenApprovals();

  String approvalTableReviewer();

  String changeInfoBlockOwner();
  String changeInfoBlockProject();
  String changeInfoBlockBranch();
  String changeInfoBlockUploaded();
  String changeInfoBlockStatus();
  String changePermalink();

  String patchSetInfoDownload();
}
