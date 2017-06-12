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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.git.validators.UploadValidationException;
import com.google.gerrit.server.git.validators.UploadValidators;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.gerrit.sshd.SshSession;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.transport.PostUploadHook;
import org.eclipse.jgit.transport.PostUploadHookChain;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.PreUploadHookChain;
import org.eclipse.jgit.transport.UploadPack;

/** Publishes Git repositories over SSH using the Git upload-pack protocol. */
final class Upload extends AbstractGitCommand {
  @Inject private ReviewDb db;

  @Inject private TransferConfig config;

  @Inject private TagCache tagCache;

  @Inject private ChangeNotes.Factory changeNotesFactory;

  @Inject @Nullable private SearchingChangeCacheImpl changeCache;

  @Inject private DynamicSet<PreUploadHook> preUploadHooks;

  @Inject private DynamicSet<PostUploadHook> postUploadHooks;

  @Inject private UploadValidators.Factory uploadValidatorsFactory;

  @Inject private SshSession session;

  @Override
  protected void runImpl() throws IOException, Failure {
    if (!projectControl.canRunUploadPack()) {
      throw new Failure(1, "fatal: upload-pack not permitted on this server");
    }

    final UploadPack up = new UploadPack(repo);
    up.setAdvertiseRefsHook(
        new VisibleRefFilter(
            tagCache, changeNotesFactory, changeCache, repo, projectControl, db, true));
    up.setPackConfig(config.getPackConfig());
    up.setTimeout(config.getTimeout());
    up.setPostUploadHook(PostUploadHookChain.newChain(Lists.newArrayList(postUploadHooks)));

    List<PreUploadHook> allPreUploadHooks = Lists.newArrayList(preUploadHooks);
    allPreUploadHooks.add(
        uploadValidatorsFactory.create(project, repo, session.getRemoteAddressAsString()));
    up.setPreUploadHook(PreUploadHookChain.newChain(allPreUploadHooks));
    try {
      up.upload(in, out, err);
      session.setPeerAgent(up.getPeerUserAgent());
    } catch (UploadValidationException e) {
      // UploadValidationException is used by the UploadValidators to
      // stop the uploadPack. We do not want this exception to go beyond this
      // point otherwise it would print a stacktrace in the logs and return an
      // internal server error to the client.
      if (!e.isOutput()) {
        up.sendMessage(e.getMessage());
      }
    }
  }
}
