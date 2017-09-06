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
    private String subject;
    private Boolean draft;
    private Boolean edit;
    private Boolean isPrivate;
    private Boolean wip;

    public Input(Change change) {
      this.change = change;
    }

    public Input setPrivate(boolean isPrivate) {
      this.isPrivate = isPrivate;
      return this;
    }

    public Input setDraft(boolean draft) {
      this.draft = draft;
      return this;
    }

    public Input setEdit(boolean edit) {
      this.edit = edit;
      return this;
    }

    public Input setWorkInProgress(boolean wip) {
      this.wip = wip;
      return this;
    }

    public Input setSubject(String subject) {
      this.subject = subject;
      return this;
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
      return edit == null ? false : edit;
    }

    public boolean isPrivate() {
      return isPrivate == null ? change.isPrivate() : isPrivate;
    }

    public boolean isWorkInProgress() {
      return wip == null ? change.isWorkInProgress() : wip;
    }
  }

  String newChange(Input input);

  String changeUpdated(Input input);

  String changeClosed(Input input);
}
