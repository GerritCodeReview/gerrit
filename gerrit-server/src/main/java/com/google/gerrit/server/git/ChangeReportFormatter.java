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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;

public interface ChangeReportFormatter {
  public static class Input {
    private final Change change;
    private final String subject;
    private final Boolean draft;
    private final boolean edit;

    public Input(Change change) {
      this(change, null, null, false);
    }

    public Input(Change change, String subject, Boolean draft, boolean edit) {
      this.change = change;
      this.subject = subject;
      this.draft = draft;
      this.edit = edit;
    }

    public Change getChange() {
      return change;
    }

    public String getSubject() {
      return subject == null ? change.getSubject() : subject;
    }

    public boolean isDraft() {
      return draft == null ? Change.Status.DRAFT == change.getStatus() : draft;
    }

    public boolean isEdit() {
      return edit;
    }
  }

  String newChange(Input input);

  String changeUpdated(Input input);

  String changeClosed(Input input);
}
