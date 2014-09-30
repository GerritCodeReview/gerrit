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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PostReceiveHookChain;
import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Receives change upload over SSH using the Git receive-pack protocol. */
@CommandMetaData(name = "receive-pack", description = "Standard Git server side command for client side git push")
final class Receive extends AbstractGitCommand {
  private static final Logger log = LoggerFactory.getLogger(Receive.class);

  @Inject
  private AsyncReceiveCommits.Factory factory;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private TransferConfig config;

  @Inject
  private DynamicSet<ReceivePackInitializer> receivePackInitializers;

  @Inject
  private DynamicSet<PostReceiveHook> postReceiveHooks;

  private final Set<Account.Id> reviewerId = new HashSet<>();
  private final Set<Account.Id> ccId = new HashSet<>();

  @Option(name = "--reviewer", aliases = {"--re"}, metaVar = "EMAIL", usage = "request reviewer for change(s)")
  void addReviewer(final Account.Id id) {
    reviewerId.add(id);
  }

  @Option(name = "--cc", aliases = {}, metaVar = "EMAIL", usage = "CC user on change(s)")
  void addCC(final Account.Id id) {
    ccId.add(id);
  }

  @Override
  protected void runImpl() throws IOException, Failure {
    if (!projectControl.canRunReceivePack()) {
      throw new Failure(1, "fatal: receive-pack not permitted on this server");
    }

    final ReceiveCommits receive = factory.create(projectControl, repo)
        .getReceiveCommits();

    Capable r = receive.canUpload();
    if (r != Capable.OK) {
      throw new UnloggedFailure(1, "\nfatal: " + r.getMessage());
    }

    verifyProjectVisible("reviewer", reviewerId);
    verifyProjectVisible("CC", ccId);

    receive.addReviewers(reviewerId);
    receive.addExtraCC(ccId);

    final ReceivePack rp = receive.getReceivePack();
    rp.setRefLogIdent(currentUser.newRefLogIdent());
    rp.setTimeout(config.getTimeout());
    rp.setMaxObjectSizeLimit(config.getEffectiveMaxObjectSizeLimit(
        projectControl.getProjectState()));
    init(rp);
    rp.setPostReceiveHook(PostReceiveHookChain.newChain(
        Lists.newArrayList(postReceiveHooks)));
    try {
      rp.receive(in, out, err);
    } catch (UnpackException badStream) {
      // In case this was caused by the user pushing an object whose size
      // is larger than the receive.maxObjectSizeLimit gerrit.config parameter
      // we want to present this error to the user
      if (badStream.getCause() instanceof TooLargeObjectInPackException) {
        StringBuilder msg = new StringBuilder();
        msg.append("Receive error on project \"")
           .append(projectControl.getProject().getName()).append("\"");
        msg.append(" (user ");
        msg.append(currentUser.getAccount().getUserName());
        msg.append(" account ");
        msg.append(currentUser.getAccountId());
        msg.append("): ");
        msg.append(badStream.getCause().getMessage());
        log.info(msg.toString());
        throw new UnloggedFailure(128, "error: " + badStream.getCause().getMessage());
      }

      // This may have been triggered by branch level access controls.
      // Log what the heck is going on, as detailed as we can.
      //
      StringBuilder msg = new StringBuilder();
      msg.append("Unpack error on project \"")
         .append(projectControl.getProject().getName()).append("\":\n");

      msg.append("  AdvertiseRefsHook: ").append(rp.getAdvertiseRefsHook());
      if (rp.getAdvertiseRefsHook() == AdvertiseRefsHook.DEFAULT) {
        msg.append("DEFAULT");
      } else if (rp.getAdvertiseRefsHook() instanceof VisibleRefFilter) {
        msg.append("VisibleRefFilter");
      } else {
        msg.append(rp.getAdvertiseRefsHook().getClass());
      }
      msg.append("\n");

      if (rp.getAdvertiseRefsHook() instanceof VisibleRefFilter) {
        Map<String, Ref> adv = rp.getAdvertisedRefs();
        msg.append("  Visible references (").append(adv.size()).append("):\n");
        for (Ref ref : adv.values()) {
          msg.append("  - ").append(ref.getObjectId().abbreviate(8).name())
             .append(" ").append(ref.getName()).append("\n");
        }

        Map<String, Ref> allRefs =
            rp.getRepository().getRefDatabase().getRefs(RefDatabase.ALL);
        List<Ref> hidden = new ArrayList<>();
        for (Ref ref : allRefs.values()) {
          if (!adv.containsKey(ref.getName())) {
            hidden.add(ref);
          }
        }

        msg.append("  Hidden references (").append(hidden.size()).append("):\n");
        for (Ref ref : hidden) {
          msg.append("  - ").append(ref.getObjectId().abbreviate(8).name())
              .append(" ").append(ref.getName()).append("\n");
        }
      }

      IOException detail = new IOException(msg.toString(), badStream);
      throw new Failure(128, "fatal: Unpack error, check server log", detail);
    }
  }

  private void init(ReceivePack rp) {
    for (ReceivePackInitializer initializer : receivePackInitializers) {
      initializer.init(projectControl.getProject().getNameKey(), rp);
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
