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

package com.google.gerrit.server.api.changes;

import com.google.gerrit.extensions.api.changes.ChangeEditApi;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeEditResource;
import com.google.gerrit.server.change.ChangeEdits;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.DeleteChangeEdit;
import com.google.gerrit.server.change.PublishChangeEdit;
import com.google.gerrit.server.change.RebaseChangeEdit;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Optional;

public class ChangeEditApiImpl implements ChangeEditApi {
  interface Factory {
    ChangeEditApiImpl create(ChangeResource changeResource);
  }

  private final ChangeEdits.Detail editDetail;
  private final ChangeEdits.Post changeEditsPost;
  private final DeleteChangeEdit deleteChangeEdit;
  private final RebaseChangeEdit.Rebase rebaseChangeEdit;
  private final PublishChangeEdit.Publish publishChangeEdit;
  private final ChangeEdits.Get changeEditsGet;
  private final ChangeEdits.Put changeEditsPut;
  private final ChangeEdits.DeleteContent changeEditDeleteContent;
  private final ChangeEdits.GetMessage getChangeEditCommitMessage;
  private final ChangeEdits.EditMessage modifyChangeEditCommitMessage;
  private final ChangeEdits changeEdits;
  private final ChangeResource changeResource;

  @Inject
  public ChangeEditApiImpl(
      ChangeEdits.Detail editDetail,
      ChangeEdits.Post changeEditsPost,
      DeleteChangeEdit deleteChangeEdit,
      RebaseChangeEdit.Rebase rebaseChangeEdit,
      PublishChangeEdit.Publish publishChangeEdit,
      ChangeEdits.Get changeEditsGet,
      ChangeEdits.Put changeEditsPut,
      ChangeEdits.DeleteContent changeEditDeleteContent,
      ChangeEdits.GetMessage getChangeEditCommitMessage,
      ChangeEdits.EditMessage modifyChangeEditCommitMessage,
      ChangeEdits changeEdits,
      @Assisted ChangeResource changeResource) {
    this.editDetail = editDetail;
    this.changeEditsPost = changeEditsPost;
    this.deleteChangeEdit = deleteChangeEdit;
    this.rebaseChangeEdit = rebaseChangeEdit;
    this.publishChangeEdit = publishChangeEdit;
    this.changeEditsGet = changeEditsGet;
    this.changeEditsPut = changeEditsPut;
    this.changeEditDeleteContent = changeEditDeleteContent;
    this.getChangeEditCommitMessage = getChangeEditCommitMessage;
    this.modifyChangeEditCommitMessage = modifyChangeEditCommitMessage;
    this.changeEdits = changeEdits;
    this.changeResource = changeResource;
  }

  @Override
  public Optional<EditInfo> get() throws RestApiException {
    try {
      Response<EditInfo> edit = editDetail.apply(changeResource);
      return edit.isNone() ? Optional.empty() : Optional.of(edit.value());
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot retrieve change edit", e);
    }
  }

  @Override
  public void create() throws RestApiException {
    try {
      changeEditsPost.apply(changeResource, null);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot create change edit", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteChangeEdit.apply(changeResource, new DeleteChangeEdit.Input());
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot delete change edit", e);
    }
  }

  @Override
  public void rebase() throws RestApiException {
    try {
      rebaseChangeEdit.apply(changeResource, null);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot rebase change edit", e);
    }
  }

  @Override
  public void publish() throws RestApiException {
    publish(null);
  }

  @Override
  public void publish(PublishChangeEditInput publishChangeEditInput) throws RestApiException {
    try {
      publishChangeEdit.apply(changeResource, publishChangeEditInput);
    } catch (IOException | OrmException | UpdateException e) {
      throw new RestApiException("Cannot publish change edit", e);
    }
  }

  @Override
  public Optional<BinaryResult> getFile(String filePath) throws RestApiException {
    try {
      ChangeEditResource changeEditResource = getChangeEditResource(filePath);
      Response<BinaryResult> fileResponse = changeEditsGet.apply(changeEditResource);
      return fileResponse.isNone() ? Optional.empty() : Optional.of(fileResponse.value());
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot retrieve file of change edit", e);
    }
  }

  @Override
  public void renameFile(String oldFilePath, String newFilePath) throws RestApiException {
    try {
      ChangeEdits.Post.Input renameInput = new ChangeEdits.Post.Input();
      renameInput.oldPath = oldFilePath;
      renameInput.newPath = newFilePath;
      changeEditsPost.apply(changeResource, renameInput);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot rename file of change edit", e);
    }
  }

  @Override
  public void restoreFile(String filePath) throws RestApiException {
    try {
      ChangeEdits.Post.Input restoreInput = new ChangeEdits.Post.Input();
      restoreInput.restorePath = filePath;
      changeEditsPost.apply(changeResource, restoreInput);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot restore file of change edit", e);
    }
  }

  @Override
  public void modifyFile(String filePath, RawInput newContent) throws RestApiException {
    try {
      changeEditsPut.apply(changeResource.getControl(), filePath, newContent);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot modify file of change edit", e);
    }
  }

  @Override
  public void deleteFile(String filePath) throws RestApiException {
    try {
      changeEditDeleteContent.apply(changeResource.getControl(), filePath);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot delete file of change edit", e);
    }
  }

  @Override
  public String getCommitMessage() throws RestApiException {
    try {
      try (BinaryResult binaryResult = getChangeEditCommitMessage.apply(changeResource)) {
        return binaryResult.asString();
      }
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot get commit message of change edit", e);
    }
  }

  @Override
  public void modifyCommitMessage(String newCommitMessage) throws RestApiException {
    ChangeEdits.EditMessage.Input input = new ChangeEdits.EditMessage.Input();
    input.message = newCommitMessage;
    try {
      modifyChangeEditCommitMessage.apply(changeResource, input);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot modify commit message of change edit", e);
    }
  }

  private ChangeEditResource getChangeEditResource(String filePath)
      throws ResourceNotFoundException, AuthException, IOException, OrmException {
    return changeEdits.parse(changeResource, IdString.fromDecoded(filePath));
  }
}
