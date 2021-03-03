// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.entities.RefNames.patchSetRef;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInfoDiffer;
import com.google.gerrit.extensions.common.ChangeInfoDifference;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class MetaDiff implements RestReadView<ChangeResource>, DynamicOptions.BeanReceiver {

  private final GetChange delegate;
  private final GitRepositoryManager repoManager;

  @Option(name = "-o", usage = "Output options")
  public void addOption(ListChangesOption o) {
    delegate.addOption(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    delegate.setOptionFlagsHex(hex);
  }

  @Option(name = "--old", usage = "old NoteDb meta SHA-1")
  String oldMetaRevId = "";

  public void setOldMetaRevId(String oldMetaRevId) {
    checkNotNull(oldMetaRevId);
    this.oldMetaRevId = oldMetaRevId;
  }

  @Option(name = "--meta", usage = "new NoteDb meta SHA-1")
  String metaRevId = "";

  public void setNewMetaRevId(String metaRevId) {
    checkNotNull(metaRevId);
    this.metaRevId = metaRevId;
  }

  @Inject
  MetaDiff(GetChange delegate, GitRepositoryManager repoManager) {
    this.delegate = delegate;
    this.repoManager = repoManager;
  }

  @Override
  public void setDynamicBean(String plugin, DynamicBean dynamicBean) {
    delegate.setDynamicBean(plugin, dynamicBean);
  }

  @Override
  public Class<? extends DynamicOptions.BeanReceiver> getExportedBeanReceiver() {
    return delegate.getExportedBeanReceiver();
  }

  @Override
  public Response<ChangeInfoDifference> apply(ChangeResource resource)
      throws BadRequestException, PreconditionFailedException, IOException {
    return Response.ok(
        ChangeInfoDiffer.getDifference(getOldChangeInfo(resource), getNewChangeInfo(resource)));
  }

  private ChangeInfo getOldChangeInfo(ChangeResource resource)
      throws BadRequestException, PreconditionFailedException, IOException {
    delegate.setMetaRevId(oldMetaRevId.isEmpty() ? getPreviousMetaRevId(resource) : oldMetaRevId);
    ChangeInfo oldChangeInfo;
    try {
      oldChangeInfo = delegate.apply(resource).value();
    } catch (PreconditionFailedException e) {
      delegate.setMetaRevId(ObjectId.zeroId().getName());
      oldChangeInfo = delegate.apply(resource).value();
    }
    return oldChangeInfo;
  }

  private String getPreviousMetaRevId(ChangeResource resource) throws IOException {
    PatchSet.Id previousPatchSetId =
        resource.getNotes().getPatchSets().lowerKey(resource.getChange().currentPatchSetId());
    try (Repository repo = repoManager.openRepository(resource.getProject())) {
      return repo.exactRef(
              previousPatchSetId != null
                  ? patchSetRef(previousPatchSetId)
                  : changeMetaRef(resource.getId()))
          .getObjectId()
          .getName();
    }
  }

  private ChangeInfo getNewChangeInfo(ChangeResource resource)
      throws BadRequestException, PreconditionFailedException, IOException {
    delegate.setMetaRevId(metaRevId.isEmpty() ? getCurrentMetaRevId(resource) : metaRevId);
    return delegate.apply(resource).value();
  }

  private String getCurrentMetaRevId(ChangeResource resource) throws IOException {
    try (Repository repo = repoManager.openRepository(resource.getProject())) {
      return repo.exactRef(changeMetaRef(resource.getId())).getObjectId().getName();
    }
  }
}
