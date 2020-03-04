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

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.git.DefaultAdvertiseRefsHook;
import com.google.gerrit.server.git.receive.AsyncReceiveCommits;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.Option;

/** Receives change upload over SSH using the Git receive-pack protocol. */
@CommandMetaData(
    name = "receive-pack",
    description = "Standard Git server side command for client side git push")
final class Receive extends AbstractGitCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject private AsyncReceiveCommits.Factory factory;
  @Inject private PermissionBackend permissionBackend;

  private final SetMultimap<ReviewerStateInternal, Account.Id> reviewers =
      MultimapBuilder.hashKeys(2).hashSetValues().build();

  @Option(
      name = "--reviewer",
      aliases = {"--re"},
      metaVar = "EMAIL",
      usage = "request reviewer for change(s)")
  void addReviewer(Account.Id id) {
    reviewers.put(ReviewerStateInternal.REVIEWER, id);
  }

  @Option(
      name = "--cc",
      aliases = {},
      metaVar = "EMAIL",
      usage = "CC user on change(s)")
  void addCC(Account.Id id) {
    reviewers.put(ReviewerStateInternal.CC, id);
  }

  @Override
  protected void runImpl() throws IOException, Failure {
    try {
      permissionBackend
          .user(session.getUser())
          .project(project.getNameKey())
          .check(ProjectPermission.RUN_RECEIVE_PACK);
    } catch (AuthException e) {
      throw new Failure(1, "fatal: receive-pack not permitted on this server", e);
    } catch (PermissionBackendException e) {
      throw new Failure(1, "fatal: unable to check permissions " + e);
    }

    AsyncReceiveCommits arc =
        factory.create(projectState, session.getUser().asIdentifiedUser(), repo, null);

    try {
      Capable r = arc.canUpload();
      if (r != Capable.OK) {
        throw die(r.getMessage());
      }
    } catch (PermissionBackendException e) {
      throw die(e.getMessage());
    }

    ReceivePack rp = arc.getReceivePack();
    try {
      rp.receive(in, out, err);
      session.setPeerAgent(rp.getPeerUserAgent());
    } catch (UnpackException badStream) {
      // In case this was caused by the user pushing an object whose size
      // is larger than the receive.maxObjectSizeLimit gerrit.config parameter
      // we want to present this error to the user
      if (badStream.getCause() instanceof TooLargeObjectInPackException) {
        StringBuilder msg = new StringBuilder();
        msg.append("Receive error on project \"").append(projectState.getName()).append("\"");
        msg.append(" (user ");
        msg.append(session.getUser().getUserName().orElse(null));
        msg.append(" account ");
        msg.append(session.getUser().getAccountId());
        msg.append("): ");
        msg.append(badStream.getCause().getMessage());
        logger.atInfo().log(msg.toString());
        throw new UnloggedFailure(128, "error: " + badStream.getCause().getMessage());
      }

      // This may have been triggered by branch level access controls.
      // Log what the heck is going on, as detailed as we can.
      //
      StringBuilder msg = new StringBuilder();
      msg.append("Unpack error on project \"").append(projectState.getName()).append("\":\n");

      msg.append("  AdvertiseRefsHook: ").append(rp.getAdvertiseRefsHook());
      if (rp.getAdvertiseRefsHook() == AdvertiseRefsHook.DEFAULT) {
        msg.append("DEFAULT");
      } else if (rp.getAdvertiseRefsHook() instanceof DefaultAdvertiseRefsHook) {
        msg.append("DefaultAdvertiseRefsHook");
      } else {
        msg.append(rp.getAdvertiseRefsHook().getClass());
      }
      msg.append("\n");

      if (rp.getAdvertiseRefsHook() instanceof DefaultAdvertiseRefsHook) {
        Map<String, Ref> adv = rp.getAdvertisedRefs();
        msg.append("  Visible references (").append(adv.size()).append("):\n");
        for (Ref ref : adv.values()) {
          msg.append("  - ")
              .append(ref.getObjectId().abbreviate(8).name())
              .append(" ")
              .append(ref.getName())
              .append("\n");
        }

        List<Ref> allRefs = rp.getRepository().getRefDatabase().getRefs();
        List<Ref> hidden = new ArrayList<>();
        for (Ref ref : allRefs) {
          if (!adv.containsKey(ref.getName())) {
            hidden.add(ref);
          }
        }

        msg.append("  Hidden references (").append(hidden.size()).append("):\n");
        for (Ref ref : hidden) {
          msg.append("  - ")
              .append(ref.getObjectId().abbreviate(8).name())
              .append(" ")
              .append(ref.getName())
              .append("\n");
        }
      }

      IOException detail = new IOException(msg.toString(), badStream);
      throw new Failure(128, "fatal: Unpack error, check server log", detail);
    }
  }
}
