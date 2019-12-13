// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

/** Collection of validators to run before Gerrit sends pack data to a client. */
public class UploadValidators implements PreUploadHook {

  private final PluginSetContext<UploadValidationListener> uploadValidationListeners;
  private final Project project;
  private final Repository repository;
  private final String remoteHost;

  public interface Factory {
    UploadValidators create(Project project, Repository repository, String remoteAddress);
  }

  @Inject
  UploadValidators(
      PluginSetContext<UploadValidationListener> uploadValidationListeners,
      @Assisted Project project,
      @Assisted Repository repository,
      @Assisted String remoteHost) {
    this.uploadValidationListeners = uploadValidationListeners;
    this.project = project;
    this.repository = repository;
    this.remoteHost = remoteHost;
  }

  @Override
  public void onSendPack(
      UploadPack up, Collection<? extends ObjectId> wants, Collection<? extends ObjectId> haves)
      throws ServiceMayNotContinueException {
    try {
      uploadValidationListeners.runEach(
          l -> l.onPreUpload(repository, project, remoteHost, up, wants, haves),
          ValidationException.class);
    } catch (ValidationException e) {
      throw new UploadValidationException(e.getMessage());
    }
  }

  @Override
  public void onBeginNegotiateRound(
      UploadPack up, Collection<? extends ObjectId> wants, int cntOffered)
      throws ServiceMayNotContinueException {
    try {
      uploadValidationListeners.runEach(
          l -> l.onBeginNegotiate(repository, project, remoteHost, up, wants, cntOffered),
          ValidationException.class);
    } catch (ValidationException e) {
      throw new UploadValidationException(e.getMessage());
    }
  }

  @Override
  public void onEndNegotiateRound(
      UploadPack up,
      Collection<? extends ObjectId> wants,
      int cntCommon,
      int cntNotFound,
      boolean ready)
      throws ServiceMayNotContinueException {}
}
