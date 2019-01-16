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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.ChangeEditApi;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.client.ChangeEditDetailOption;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeEditResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.restapi.change.ChangeEdits;
import com.google.gerrit.server.restapi.change.DeleteChangeEdit;
import com.google.gerrit.server.restapi.change.PublishChangeEdit;
import com.google.gerrit.server.restapi.change.RebaseChangeEdit;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Optional;

public class ChangeEditApiImpl implements ChangeEditApi {
  interface Factory {
    ChangeEditApiImpl create(ChangeResource changeResource);
  }

  private final Provider<ChangeEdits.Detail> editDetailProvider;
  private final ChangeEdits.Post changeEditsPost;
  private final DeleteChangeEdit deleteChangeEdit;
  private final RebaseChangeEdit rebaseChangeEdit;
  private final PublishChangeEdit publishChangeEdit;
  private final Provider<ChangeEdits.Get> changeEditsGetProvider;
  private final ChangeEdits.Put changeEditsPut;
  private final ChangeEdits.DeleteContent changeEditDeleteContent;
  private final Provider<ChangeEdits.GetMessage> getChangeEditCommitMessageProvider;
  private final ChangeEdits.EditMessage modifyChangeEditCommitMessage;
  private final ChangeEdits changeEdits;
  private final ChangeResource changeResource;

  @Inject
  public ChangeEditApiImpl(
      Provider<ChangeEdits.Detail> editDetailProvider,
      ChangeEdits.Post changeEditsPost,
      DeleteChangeEdit deleteChangeEdit,
      RebaseChangeEdit rebaseChangeEdit,
      PublishChangeEdit publishChangeEdit,
      Provider<ChangeEdits.Get> changeEditsGetProvider,
      ChangeEdits.Put changeEditsPut,
      ChangeEdits.DeleteContent changeEditDeleteContent,
      Provider<ChangeEdits.GetMessage> getChangeEditCommitMessageProvider,
      ChangeEdits.EditMessage modifyChangeEditCommitMessage,
      ChangeEdits changeEdits,
      @Assisted ChangeResource changeResource) {
    this.editDetailProvider = editDetailProvider;
    this.changeEditsPost = changeEditsPost;
    this.deleteChangeEdit = deleteChangeEdit;
    this.rebaseChangeEdit = rebaseChangeEdit;
    this.publishChangeEdit = publishChangeEdit;
    this.changeEditsGetProvider = changeEditsGetProvider;
    this.changeEditsPut = changeEditsPut;
    this.changeEditDeleteContent = changeEditDeleteContent;
    this.getChangeEditCommitMessageProvider = getChangeEditCommitMessageProvider;
    this.modifyChangeEditCommitMessage = modifyChangeEditCommitMessage;
    this.changeEdits = changeEdits;
    this.changeResource = changeResource;
  }

  @Override
  public ChangeEditDetailRequest detail() throws RestApiException {
    try {
      return new ChangeEditDetailRequest() {
        @Override
        public Optional<EditInfo> get() throws RestApiException {
          return ChangeEditApiImpl.this.get(this);
        }
      };
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change edit", e);
    }
  }

  private Optional<EditInfo> get(ChangeEditDetailRequest r) throws RestApiException {
    try {
      ChangeEdits.Detail editDetail = editDetailProvider.get();
      editDetail.setBase(r.getBase());
      editDetail.setList(r.options().contains(ChangeEditDetailOption.LIST_FILES));
      editDetail.setDownloadCommands(
          r.options().contains(ChangeEditDetailOption.DOWNLOAD_COMMANDS));
      Response<EditInfo> edit = editDetail.apply(changeResource);
      return edit.isNone() ? Optional.empty() : Optional.of(edit.value());
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change edit", e);
    }
  }

  @Override
  public Optional<EditInfo> get() throws RestApiException {
    try {
      Response<EditInfo> edit = editDetailProvider.get().apply(changeResource);
      return edit.isNone() ? Optional.empty() : Optional.of(edit.value());
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change edit", e);
    }
  }

  @Override
  public void create() throws RestApiException {
    try {
      changeEditsPost.apply(changeResource, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot create change edit", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    try {
      deleteChangeEdit.apply(changeResource, new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot delete change edit", e);
    }
  }

  @Override
  public void rebase() throws RestApiException {
    try {
      rebaseChangeEdit.apply(changeResource, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot rebase change edit", e);
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
    } catch (Exception e) {
      throw asRestApiException("Cannot publish change edit", e);
    }
  }

  @Override
  public Optional<BinaryResult> getFile(String filePath) throws RestApiException {
    try {
      ChangeEditResource changeEditResource = getChangeEditResource(filePath);
      Response<BinaryResult> fileResponse = changeEditsGetProvider.get().apply(changeEditResource);
      return fileResponse.isNone() ? Optional.empty() : Optional.of(fileResponse.value());
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve file of change edit", e);
    }
  }

  @Override
  public void renameFile(String oldFilePath, String newFilePath) throws RestApiException {
    try {
      ChangeEdits.Post.Input renameInput = new ChangeEdits.Post.Input();
      renameInput.oldPath = oldFilePath;
      renameInput.newPath = newFilePath;
      changeEditsPost.apply(changeResource, renameInput);
    } catch (Exception e) {
      throw asRestApiException("Cannot rename file of change edit", e);
    }
  }

  @Override
  public void restoreFile(String filePath) throws RestApiException {
    try {
      ChangeEdits.Post.Input restoreInput = new ChangeEdits.Post.Input();
      restoreInput.restorePath = filePath;
      changeEditsPost.apply(changeResource, restoreInput);
    } catch (Exception e) {
      throw asRestApiException("Cannot restore file of change edit", e);
    }
  }

  @Override
  public void modifyFile(String filePath, RawInput newContent) throws RestApiException {
    try {
      changeEditsPut.apply(changeResource, filePath, newContent);
    } catch (Exception e) {
      throw asRestApiException("Cannot modify file of change edit", e);
    }
  }

  @Override
  public void deleteFile(String filePath) throws RestApiException {
    try {
      changeEditDeleteContent.apply(changeResource, filePath);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete file of change edit", e);
    }
  }

  @Override
  public String getCommitMessage() throws RestApiException {
    try {
      try (BinaryResult binaryResult =
          getChangeEditCommitMessageProvider.get().apply(changeResource)) {
        return binaryResult.asString();
      }
    } catch (Exception e) {
      throw asRestApiException("Cannot get commit message of change edit", e);
    }
  }

  @Override
  public void modifyCommitMessage(String newCommitMessage) throws RestApiException {
    ChangeEdits.EditMessage.Input input = new ChangeEdits.EditMessage.Input();
    input.message = newCommitMessage;
    try {
      modifyChangeEditCommitMessage.apply(changeResource, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot modify commit message of change edit", e);
    }
  }

  private ChangeEditResource getChangeEditResource(String filePath)
      throws ResourceNotFoundException, AuthException, IOException {
    return changeEdits.parse(changeResource, IdString.fromDecoded(filePath));
  }
}
