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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Optional;

/**
 * An API for the change edit of a change. A change edit is similar to a patch set and will become
 * one if it is published (by {@link #publish(PublishChangeEditInput)}). Whenever the descriptions
 * below refer to files of a change edit, they actually refer to the files of the Git tree which is
 * represented by the change edit. A change can have at most one change edit at each point in time.
 */
public interface ChangeEditApi {

  /**
   * Retrieves details regarding the change edit.
   *
   * @return an {@code Optional} containing details about the change edit if it exists, or {@code
   *     Optional.empty()}
   * @throws RestApiException if the change edit couldn't be retrieved
   */
  Optional<EditInfo> get() throws RestApiException;

  /**
   * Creates a new change edit. It has exactly the same Git tree as the current patch set of the
   * change.
   *
   * @throws RestApiException if the change edit couldn't be created or a change edit already exists
   */
  void create() throws RestApiException;

  /**
   * Deletes the change edit.
   *
   * @throws RestApiException if the change edit couldn't be deleted or a change edit wasn't present
   */
  void delete() throws RestApiException;

  /**
   * Rebases the change edit on top of the latest patch set of this change.
   *
   * @throws RestApiException if the change edit couldn't be rebased or a change edit wasn't present
   */
  void rebase() throws RestApiException;

  /**
   * Publishes the change edit using default settings. See {@link #publish(PublishChangeEditInput)}
   * for more details.
   *
   * @throws RestApiException if the change edit couldn't be published or a change edit wasn't
   *     present
   */
  void publish() throws RestApiException;

  /**
   * Publishes the change edit. Publishing means that the change edit is turned into a regular patch
   * set of the change.
   *
   * @param publishChangeEditInput a {@code PublishChangeEditInput} specifying the options which
   *     should be applied
   * @throws RestApiException if the change edit couldn't be published or a change edit wasn't
   *     present
   */
  void publish(PublishChangeEditInput publishChangeEditInput) throws RestApiException;

  /**
   * Retrieves the contents of the specified file from the change edit.
   *
   * @param filePath the path of the file
   * @return an {@code Optional} containing the contents of the file as a {@code BinaryResult} if
   *     the file exists within the change edit, or {@code Optional.empty()}
   * @throws RestApiException if the contents of the file couldn't be retrieved or a change edit
   *     wasn't present
   */
  Optional<BinaryResult> getFile(String filePath) throws RestApiException;

  /**
   * Renames a file of the change edit or moves the file to another directory. If the change edit
   * doesn't exist, it will be created based on the current patch set of the change.
   *
   * @param oldFilePath the current file path
   * @param newFilePath the desired file path
   * @throws RestApiException if the file couldn't be renamed
   */
  void renameFile(String oldFilePath, String newFilePath) throws RestApiException;

  /**
   * Restores a file of the change edit to the state in which it was before the patch set on which
   * the change edit is based. This includes the file content as well as the existence or
   * non-existence of the file. If the change edit doesn't exist, it will be created based on the
   * current patch set of the change.
   *
   * @param filePath the path of the file
   * @throws RestApiException if the file couldn't be restored to its previous state
   */
  void restoreFile(String filePath) throws RestApiException;

  /**
   * Modify the contents of the specified file of the change edit. If no content is provided, the
   * content of the file is erased but the file isn't deleted. If the change edit doesn't exist, it
   * will be created based on the current patch set of the change.
   *
   * @param filePath the path of the file which should be modified
   * @param newContent the desired content of the file
   * @throws RestApiException if the content of the file couldn't be modified
   */
  void modifyFile(String filePath, RawInput newContent) throws RestApiException;

  /**
   * Deletes the specified file from the change edit. If the change edit doesn't exist, it will be
   * created based on the current patch set of the change.
   *
   * @param filePath the path fo the file which should be deleted
   * @throws RestApiException if the file couldn't be deleted
   */
  void deleteFile(String filePath) throws RestApiException;

  /**
   * Retrieves the commit message of the change edit.
   *
   * @return the commit message of the change edit
   * @throws RestApiException if the commit message couldn't be retrieved or a change edit wasn't
   *     present
   */
  String getCommitMessage() throws RestApiException;

  /**
   * Modifies the commit message of the change edit. If the change edit doesn't exist, it will be
   * created based on the current patch set of the change.
   *
   * @param newCommitMessage the desired commit message
   * @throws RestApiException if the commit message couldn't be modified
   */
  void modifyCommitMessage(String newCommitMessage) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements ChangeEditApi {
    @Override
    public Optional<EditInfo> get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void create() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void rebase() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void publish() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void publish(PublishChangeEditInput publishChangeEditInput) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public Optional<BinaryResult> getFile(String filePath) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void renameFile(String oldFilePath, String newFilePath) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void restoreFile(String filePath) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void modifyFile(String filePath, RawInput newContent) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void deleteFile(String filePath) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String getCommitMessage() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void modifyCommitMessage(String newCommitMessage) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
