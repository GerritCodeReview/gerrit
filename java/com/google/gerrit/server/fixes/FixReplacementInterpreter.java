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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/** An interpreter for {@code FixReplacement}s. */
@Singleton
public class FixReplacementInterpreter {

  private static final Comparator<FixReplacement> ASC_RANGE_FIX_REPLACEMENT_COMPARATOR =
      Comparator.comparing(fixReplacement -> fixReplacement.range);

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
  public List<TreeModification> toTreeModifications(
      Repository repository,
      ProjectState projectState,
      ObjectId patchSetCommitId,
      List<FixReplacement> fixReplacements)
      throws ResourceNotFoundException, IOException, ResourceConflictException {
    requireNonNull(fixReplacements, "Fix replacements must not be null");

    Map<String, List<FixReplacement>> fixReplacementsPerFilePath =
        fixReplacements.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));

    List<TreeModification> treeModifications = new ArrayList<>();
    for (Map.Entry<String, List<FixReplacement>> entry : fixReplacementsPerFilePath.entrySet()) {
      TreeModification treeModification =
          toTreeModification(
              repository, projectState, patchSetCommitId, entry.getKey(), entry.getValue());
      treeModifications.add(treeModification);
    }
    return treeModifications;
  }

  private TreeModification toTreeModification(
      Repository repository,
      ProjectState projectState,
      ObjectId patchSetCommitId,
      String filePath,
      List<FixReplacement> fixReplacements)
      throws ResourceNotFoundException, IOException, ResourceConflictException {
    String fileContent = getFileContent(repository, projectState, patchSetCommitId, filePath);
    String newFileContent = getNewFileContent(fileContent, fixReplacements);
    return new ChangeFileContentModification(filePath, RawInputUtil.create(newFileContent));
  }

  private String getFileContent(
      Repository repository, ProjectState projectState, ObjectId patchSetCommitId, String filePath)
      throws ResourceNotFoundException, IOException {
    try (BinaryResult fileContent =
        fileContentUtil.getContent(repository, projectState, patchSetCommitId, filePath)) {
      return fileContent.asString();
    }
  }

  private static String getNewFileContent(String fileContent, List<FixReplacement> fixReplacements)
      throws ResourceConflictException {
    List<FixReplacement> sortedReplacements = new ArrayList<>(fixReplacements);
    sortedReplacements.sort(ASC_RANGE_FIX_REPLACEMENT_COMPARATOR);

    LineIdentifier lineIdentifier = new LineIdentifier(fileContent);
    StringModifier fileContentModifier = new StringModifier(fileContent);
    for (FixReplacement fixReplacement : sortedReplacements) {
      Comment.Range range = fixReplacement.range;
      try {
        int startLineIndex = lineIdentifier.getStartIndexOfLine(range.startLine);
        int startLineLength = lineIdentifier.getLengthOfLine(range.startLine);

        int endLineIndex = lineIdentifier.getStartIndexOfLine(range.endLine);
        int endLineLength = lineIdentifier.getLengthOfLine(range.endLine);

        if (range.startChar > startLineLength || range.endChar > endLineLength) {
          throw new ResourceConflictException(
              String.format(
                  "Range %s refers to a non-existent offset (start line length: %s,"
                      + " end line length: %s)",
                  toString(range), startLineLength, endLineLength));
        }

        int startIndex = startLineIndex + range.startChar;
        int endIndex = endLineIndex + range.endChar;
        fileContentModifier.replace(startIndex, endIndex, fixReplacement.replacement);
      } catch (StringIndexOutOfBoundsException e) {
        // Most of the StringIndexOutOfBoundsException should never occur because we reject fix
        // replacements for invalid ranges. However, we can't cover all cases for efficiency
        // reasons. For instance, we don't determine the number of lines in a file. That's why we
        // need to map this exception and thus provide a meaningful error.
        throw new ResourceConflictException(
            String.format("Cannot apply fix replacement for range %s", toString(range)), e);
      }
    }
    return fileContentModifier.getResult();
  }

  private static String toString(Comment.Range range) {
    return String.format(
        "(%s:%s - %s:%s)", range.startLine, range.startChar, range.endLine, range.endChar);
  }
}
