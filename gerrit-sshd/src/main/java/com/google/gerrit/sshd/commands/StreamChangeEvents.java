// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.ChangeHookRunner.ChangeListener;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gson.Gson;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

final class StreamChangeEvents extends BaseCommand {
  // Poll timeout
  private static final long TIMEOUT = 2;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ChangeHookRunner hooks;

  private LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();

  private Gson gson = new Gson();

  class Approval {
    String type;
    String value;

    Approval() {
    }
  }

  class Comment {
    final String type = "comment";
    String project;
    String branch;
    String change;
    String author;
    Approval[] approvals;
    String comment;

    Comment() {
    }
  }

  class ChangeMerged {
    final String type = "change-merged";
    String project;
    String branch;
    String change;
    String author;
    String description;

    ChangeMerged() {
    }
  }

  class ChangeAbandoned {
    final String type = "change-abandoned";
    String project;
    String branch;
    String change;
    String author;
    String reason;

    ChangeAbandoned() {
    }
  }

  class PatchSetCreated {
    final String type = "patchset-created";
    String project;
    String branch;
    String change;
    String patchSet;

    PatchSetCreated() {
    }
  }

  private boolean isVisible(Change change) {
    final ProjectState e = projectCache.get(change.getProject());
    if (e == null) return false;

    final ProjectControl pc = e.controlFor(currentUser);
    if (!pc.isVisible()) return false;

    if (!pc.controlForRef(change.getDest().get()).isVisible()) return false;

    return true;
  }

  private ChangeListener listener = new ChangeListener() {
    @Override
    public void onCommentAdded(Change change, PatchSet patchSet,
        Account account,
        Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> approvals,
        String comment) {
      if (!isVisible(change)) return;

      Comment c = new Comment();
      c.project = change.getProject().get();
      c.branch = change.getDest().getShortName();
      c.change = change.getKey().abbreviate();
      c.author = getDisplayName(account);
      c.comment = comment;
      if (approvals.size() > 0) {
        c.approvals = new Approval[approvals.size()];
        Iterator<Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id>> it =
            approvals.entrySet().iterator();
        for (int i = 0; i < approvals.size(); ++i) {
          Map.Entry<ApprovalCategory.Id, ApprovalCategoryValue.Id> approval =
              it.next();
          Approval a = new Approval();
          a.type = approval.getKey().get();
          a.value = Short.toString(approval.getValue().get());
          c.approvals[i] = a;
        }
      }

      queue.add(gson.toJson(c));
    }

    @Override
    public void onChangeAbandoned(Change change, Account account, String reason) {
      if (!isVisible(change)) return;

      ChangeAbandoned c = new ChangeAbandoned();
      c.project = change.getProject().get();
      c.branch = change.getDest().getShortName();
      c.change = change.getKey().get();
      c.author = getDisplayName(account);
      c.reason = reason;

      queue.add(gson.toJson(c));
    }

    @Override
    public void onChangeMerged(Change change, Account account, PatchSet patchSet) {
      if (!isVisible(change)) return;

      ChangeMerged c = new ChangeMerged();
      c.project = change.getProject().get();
      c.branch = change.getDest().getShortName();
      c.change = change.getKey().get();
      c.author = getDisplayName(account);
      c.description = change.getSubject();

      queue.add(gson.toJson(c));
    }

    @Override
    public void onPatchsetCreated(Change change, PatchSet patchSet) {
      if (!isVisible(change)) return;

      PatchSetCreated c = new PatchSetCreated();
      c.project = change.getProject().get();
      c.branch = change.getDest().getShortName();
      c.change = change.getKey().get();
      c.patchSet = Integer.toString(patchSet.getPatchSetId());

      queue.add(gson.toJson(c));
    }
  };

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        StreamChangeEvents.this.display();
      }
    });
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);

    hooks.addChangeListener(listener);

    try {
      while (!stdout.checkError()) {
        try {
          final String output = queue.poll(TIMEOUT, TimeUnit.SECONDS);
          if (output != null) {
            stdout.println(output);
            stdout.flush();
          }
        } catch (InterruptedException e) {
        }
      }
    } finally {
      hooks.removeChangeListener(listener);
    }
  }

  /**
   * Get the display name for the given account.
   *
   * @param account Account to get name for.
   * @return Name for this account.
   */
  private String getDisplayName(final Account account) {
    if (account != null) {
      String result =
          (account.getFullName() == null) ? "Anonymous Coward" : account
              .getFullName();
      if (account.getPreferredEmail() != null) {
        result += " (" + account.getPreferredEmail() + ")";
      }
      return result;
    }

    return "Anonymous Coward";
  }
}
