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

package com.google.gerrit.server.fixes;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.CommitModification;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.patch.MagicFile;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/** An interpreter for {@code FixReplacement}s. */
@Singleton
public class FixReplacementInterpreter {

  private final FileContentUtil fileContentUtil;

  @Inject
  public FixReplacementInterpreter(FileContentUtil fileContentUtil) {
    this.fileContentUtil = fileContentUtil;
  }

  /**
   * Transforms the given {@code FixReplacement}s into {@code TreeModification}s.
   *
   * @param repository the affected Git repository
   * @param projectState the affected project
   * @param patchSetCommitId the patch set which should be modified
   * @param fixReplacements the replacements which should be applied
   * @return a list of {@code TreeModification}s representing the given replacements
   * @throws ResourceNotFoundException if a file to which one of the replacements refers doesn't
   *     exist
   * @throws ResourceConflictException if the replacements can't be transformed into {@code
   *     TreeModification}s
   */
  public CommitModification toCommitModification(
      Repository repository,
      ProjectState projectState,
      ObjectId patchSetCommitId,
      List<FixReplacement> fixReplacements)
      throws BadRequestException,
          ResourceNotFoundException,
          IOException,
          ResourceConflictException {
    requireNonNull(fixReplacements, "Fix replacements must not be null");

    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        fixReplacements.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));

    CommitModification.Builder modificationBuilder = CommitModification.builder();
    for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
      if (Objects.equals(entry.getKey(), Patch.COMMIT_MSG)) {
        String newCommitMessage =
            getNewCommitMessage(repository, patchSetCommitId, entry.getValue());
        modificationBuilder.newCommitMessage(newCommitMessage);
      } else {
        TreeModification treeModification =
            toTreeModification(
                repository, projectState, patchSetCommitId, entry.getKey(), entry.getValue());
        modificationBuilder.addTreeModification(treeModification);
      }
    }
    return modificationBuilder.build();
  }

  private static String getNewCommitMessage(
      Repository repository, ObjectId patchSetCommitId, List<FixReplacement> fixReplacements)
      throws ResourceConflictException, IOException {
    try (ObjectReader reader = repository.newObjectReader()) {
      // In the magic /COMMIT_MSG file, the actual commit message is placed after some generated
      // header lines. -> Need to find out to which actual line of the commit message a replacement
      // refers.
      MagicFile commitMessageFile = MagicFile.forCommitMessage(reader, patchSetCommitId);
      int commitMessageStartLine = commitMessageFile.getStartLineOfModifiableContent();
      // Line numbers are 1-based. -> Add 1 to not move first line.
      // Move up for any additionally found lines.
      int necessaryRangeShift = -commitMessageStartLine + 1;
      ImmutableList<FixReplacement> adjustedReplacements =
          shiftRangesBy(fixReplacements, necessaryRangeShift);
      if (referToNonPositiveLine(adjustedReplacements)) {
        throw new ResourceConflictException(
            String.format("The header of the %s file cannot be modified.", Patch.COMMIT_MSG));
      }
      String commitMessage = commitMessageFile.modifiableContent();
      return FixCalculator.getNewFileContent(commitMessage, adjustedReplacements);
    }
  }

  private static ImmutableList<FixReplacement> shiftRangesBy(
      List<FixReplacement> fixReplacements, int shiftedAmount) {
    return fixReplacements.stream()
        .map(replacement -> shiftRangesBy(replacement, shiftedAmount))
        .collect(toImmutableList());
  }

  private static FixReplacement shiftRangesBy(FixReplacement fixReplacement, int shiftedAmount) {
    Range adjustedRange = new Range(fixReplacement.range);
    adjustedRange.startLine += shiftedAmount;
    adjustedRange.endLine += shiftedAmount;
    return new FixReplacement(fixReplacement.path, adjustedRange, fixReplacement.replacement);
  }

  private static boolean referToNonPositiveLine(List<FixReplacement> adjustedReplacements) {
    return adjustedReplacements.stream()
        .map(replacement -> replacement.range)
        .anyMatch(range -> range.startLine <= 0);
  }

  private TreeModification toTreeModification(
      Repository repository,
      ProjectState projectState,
      ObjectId patchSetCommitId,
      String filePath,
      List<FixReplacement> fixReplacements)
      throws BadRequestException,
          ResourceNotFoundException,
          IOException,
          ResourceConflictException {
    String fileContent = getFileContent(repository, projectState, patchSetCommitId, filePath);
    String newFileContent = FixCalculator.getNewFileContent(fileContent, fixReplacements);

    return new ChangeFileContentModification(filePath, RawInputUtil.create(newFileContent));
  }

  private String getFileContent(
      Repository repository, ProjectState projectState, ObjectId patchSetCommitId, String filePath)
      throws ResourceNotFoundException, BadRequestException, IOException {
    try (BinaryResult fileContent =
        fileContentUtil.getContent(repository, projectState, patchSetCommitId, filePath)) {
      return fileContent.asString();
    }
  }
}
