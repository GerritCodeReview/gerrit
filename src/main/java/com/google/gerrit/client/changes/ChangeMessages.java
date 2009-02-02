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

import com.google.gwt.i18n.client.Messages;

public interface ChangeMessages extends Messages {
  String accountDashboardTitle(String fullName);
  String changesStartedBy(String fullName);
  String changesReviewableBy(String fullName);
  String changesOpenInProject(String string);

  String changeScreenTitleId(int id);
  String patchSetHeader(int id);
  String loadingPatchSet(int id);
  String patchSetAction(String action, int id);

  String patchTableComments(@PluralCount int count);
  String patchTableDrafts(@PluralCount int count);

  String messageWrittenOn(String date);

  String renamedFrom(String sourcePath);
  String copiedFrom(String sourcePath);
  String otherFrom(String sourcePath);

  String needApproval(String categoryName);
  String publishComments(int change, int ps);
  String lineHeader(int line);
}
