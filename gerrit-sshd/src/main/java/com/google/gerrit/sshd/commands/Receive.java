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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.patch.DepsBypasser;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


/** Receives change upload over SSH using the Git receive-pack protocol. */
final class Receive extends AbstractGitCommand {
  @Inject
  private ReceiveCommits.Factory factory;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

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
  protected void runImpl() throws IOException, Failure, OrmException {
    final ReceiveCommits receive = factory.create(projectControl, repo);

    ReceiveCommits.Capable r = receive.canUpload();
    if (r != ReceiveCommits.Capable.OK) {
      throw new UnloggedFailure(1, "\nfatal: " + r.getMessage());
    }

    verifyProjectVisible("reviewer", reviewerId);
    verifyProjectVisible("CC", ccId);

    receive.addReviewers(reviewerId);
    receive.addExtraCC(ccId);


    final ReceivePack rp = receive.getReceivePack();
    rp.setRefLogIdent(currentUser.newRefLogIdent());
    rp.receive(in, out, err);

    DepsBypasser depsBypasser = DepsBypasser.getInstance();
    depsBypasser.validate(db);

    // Add inter git dependencies between changes
    if (depsBypasser.isAtomicCommit()) {
      final Change superCh = db.changes().get(depsBypasser.getSuperChange());

      // You will not be able to change the members of an
      // atomic commit by patching the superproject.
      // TODO A new patch on the superproject should be created by Gerrit whenever
      // a submodule is patched.
      if (superCh.currentPatchSetId().get() == 1) {
        for (final Change.Id chId : depsBypasser.getAtomicMembers()) {
          db.changes().atomicUpdate(chId,
              new AtomicUpdate<Change>() {
                @Override
                public Change update(Change change) {
                  change.setAtomicId(superCh.getId());
                  return change;
                }
              });
        }
      }
    }
    depsBypasser.reset();
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
