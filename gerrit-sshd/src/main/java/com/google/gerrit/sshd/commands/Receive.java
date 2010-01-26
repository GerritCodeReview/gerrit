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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.git.ReceiveCommits.MessageListener;
import com.google.inject.Inject;

import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/** Receives change upload over SSH using the Git receive-pack protocol. */
final class Receive extends AbstractGitCommand {
  @Inject
  private ReceiveCommits.Factory factory;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  @CanonicalWebUrl
  @Nullable
  private String canonicalWebUrl;

  private final Set<Account.Id> reviewerId = new HashSet<Account.Id>();
  private final Set<Account.Id> ccId = new HashSet<Account.Id>();

  @Option(name = "--reviewer", aliases = {"--re"}, multiValued = true, metaVar = "EMAIL", usage = "request reviewer for change(s)")
  void addReviewer(final Account.Id id) {
    reviewerId.add(id);
  }

  @Option(name = "--cc", aliases = {}, multiValued = true, metaVar = "EMAIL", usage = "CC user on change(s)")
  void addCC(final Account.Id id) {
    ccId.add(id);
  }

  @Override
  protected void runImpl() throws IOException, Failure {
    final ReceiveCommits receive = factory.create(projectControl, repo);
    final PrintWriter msg = toPrintWriter(err);

    ReceiveCommits.Capable r = receive.canUpload();
    if (r != ReceiveCommits.Capable.OK) {
      throw new UnloggedFailure(1, "\nfatal: " + r.getMessage());
    }

    verifyProjectVisible("reviewer", reviewerId);
    verifyProjectVisible("CC", ccId);

    receive.setMessageListener(new MessageListener() {
      @Override
      public void warn(String warning) {
        msg.print("warning: " + warning + "\n");
        msg.flush();
      }
    });
    receive.addReviewers(reviewerId);
    receive.addExtraCC(ccId);

    final ReceivePack rp = receive.getReceivePack();
    rp.setRefLogIdent(currentUser.newRefLogIdent());
    rp.receive(in, out, err);

    if (!receive.getNewChanges().isEmpty() && canonicalWebUrl != null) {
      // Make sure there isn't anything buffered; we want to give the
      // push client a chance to display its status report before we
      // show our own messages on standard error.
      //
      out.flush();

      final String url = canonicalWebUrl;
      msg.write("\nNew Changes:\n");
      for (final Change.Id c : receive.getNewChanges()) {
        msg.write("  " + url + c.get() + "\n");
      }
      msg.write('\n');
      msg.flush();
    }
  }

  private void verifyProjectVisible(final String type, final Set<Account.Id> who)
      throws UnloggedFailure {
    for (final Account.Id id : who) {
      final IdentifiedUser user = identifiedUserFactory.create(id);
      if (!projectControl.forUser(user).isVisible()) {
        throw new UnloggedFailure(1, type + " "
            + user.getAccount().getFullName() + " cannot access the project");
      }
    }
  }
}
