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

import com.google.gerrit.extensions.client.ChangeEditDetailOption;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * An API for the change edit of a change. A change edit is similar to a patch set and will become
 * one if it is published (by {@link #publish(PublishChangeEditInput)}). Whenever the descriptions
 * below refer to files of a change edit, they actually refer to the files of the Git tree which is
 * represented by the change edit. A change can have at most one change edit at each point in time.
 */
public interface ChangeEditApi {

  abstract class ChangeEditDetailRequest {
    private String base;
    private EnumSet<ChangeEditDetailOption> options = EnumSet.noneOf(ChangeEditDetailOption.class);

    public abstract Optional<EditInfo> get() throws RestApiException;

    public ChangeEditDetailRequest withBase(String base) {
      this.base = base;
      return this;
    }

    public ChangeEditDetailRequest withOption(ChangeEditDetailOption option) {
      this.options.add(option);
      return this;
    }

    public String getBase() {
      return base;
    }

    public Set<ChangeEditDetailOption> options() {
      return options;
    }
  }

  ChangeEditDetailRequest detail() throws RestApiException;

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
   * Rebases the change edit on top of the latest patch set of this change.
   *
   * @param input params for rebasing the change edit
   * @throws RestApiException if the change edit couldn't be rebased or a change edit wasn't present
   */
  EditInfo rebase(RebaseChangeEditInput input) throws RestApiException;

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
  default void modifyFile(String filePath, RawInput newContent) throws RestApiException {
    FileContentInput input = new FileContentInput();
    input.content = newContent;
    modifyFile(filePath, input);
  }

  /**
   * Modify the contents of the specified file of the change edit. If no content is provided, the
   * content of the file is erased but the file isn't deleted. If the change edit doesn't exist, it
   * will be created based on the current patch set of the change.
   *
   * @param filePath the path of the file which should be modified
   * @param input the desired content of the file
   * @throws RestApiException if the content of the file couldn't be modified
   */
  void modifyFile(String filePath, FileContentInput input) throws RestApiException;

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
   * Updates the author/committer of the change edit. If the change edit doesn't exist, it will be
   * created based on the current patch set of the change.
   *
   * @param name the name of the author/committer
   * @param email the email of the author/committer
   * @param type the type of the identity being edited
   * @throws RestApiException if the author/committer identity couldn't be updated
   */
  void modifyIdentity(String name, String email, ChangeEditIdentityType type)
      throws RestApiException;
}
