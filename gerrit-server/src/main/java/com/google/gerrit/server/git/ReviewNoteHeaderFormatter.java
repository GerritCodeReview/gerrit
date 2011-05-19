// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formatters for code review note headers.
 * <p>
 * This class provides a builder like interface for building the content of a
 * code review note. After instantiation, call as many as necessary
 * <code>append...(...)</code> methods and, at the end, call the
 * {@link #toString()} method to get the built note content.
 */
class ReviewNoteHeaderFormatter {

  private final DateFormat rfc2822DateFormatter;
  private final StringBuilder sb = new StringBuilder();

  ReviewNoteHeaderFormatter(TimeZone tz) {
    rfc2822DateFormatter =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    rfc2822DateFormatter.setCalendar(Calendar.getInstance(tz, Locale.US));
  }

  void appendChangeId(Change.Key changeKey) {
    sb.append("Change-Id: ").append(changeKey.get()).append("\n");
  }

  void appendApproval(ApprovalCategory category,
      short value, Account user) {
    // TODO: use category.getLabel() when available
    sb.append(category.getName().replace(' ', '-'));
    sb.append(value < 0 ? "-" : "+").append(Math.abs(value)).append(": ");
    appendUserData(user);
    sb.append("\n");
  }

  private void appendUserData(Account user) {
    boolean needSpace = false;
    boolean wroteData = false;

    if (user.getFullName() != null && ! user.getFullName().isEmpty()) {
      sb.append(user.getFullName());
      needSpace = true;
      wroteData = true;
    }

    if (user.getPreferredEmail() != null && ! user.getPreferredEmail().isEmpty()) {
      if (needSpace) {
        sb.append(" ");
      }
      sb.append("<").append(user.getPreferredEmail()).append(">");
      wroteData = true;
    }

    if (!wroteData) {
      sb.append("Anonymous Coward #").append(user.getId());
    }
  }

  void appendProject(String projectName) {
    sb.append("Project: ").append(projectName).append("\n");
  }

  void appendBranch(Branch.NameKey branch) {
    sb.append("Branch: ").append(branch.get()).append("\n");
  }

  void appendSubmittedBy(Account user) {
    sb.append("Submitted-by: ");
    appendUserData(user);
    sb.append("\n");
  }

  void appendSubmittedAt(Date date) {
    sb.append("Submitted-at: ").append(rfc2822DateFormatter.format(date))
        .append("\n");
  }

  void appendReviewedOn(String canonicalWebUrl, Change.Id changeId) {
    sb.append("Reviewed-on: ").append(canonicalWebUrl).append(changeId.get())
        .append("\n");
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
