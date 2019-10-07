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
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Comment.Range;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.text.AbstractDocument.Content;
import org.eclipse.jgit.diff.Edit;
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

  public static Map<String, List<FixReplacement>> getFixReplacementsGroupByFilePath(List<FixReplacement> fixReplacements) {
    return fixReplacements.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));

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
        getFixReplacementsGroupByFilePath(fixReplacements);

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

  public static FixResult calculateFix(Text oldContent, List<FixReplacement> fixReplacements) {
    List<FixReplacement> sortedReplacements = new ArrayList<>(fixReplacements);
    sortedReplacements.sort(ASC_RANGE_FIX_REPLACEMENT_COMPARATOR);
    ContentCollector collector = new ContentCollector(oldContent);
    CharacterPosition nextContentPosition = new CharacterPosition(1, 0);
    for (FixReplacement fixReplacement : sortedReplacements) {
      CharacterPosition fixRangeStart = new CharacterPosition(fixReplacement.range.startLine, fixReplacement.range.startChar);
      if(nextContentPosition.before(fixRangeStart)) {
        collector.addOldContent(nextContentPosition, fixRangeStart);
      }
      CharacterPosition fixRangeEnd = new CharacterPosition(fixReplacement.range.endLine, fixReplacement.range.endChar);
      collector.addReplacement(fixRangeStart, fixRangeEnd, fixReplacement.replacement);
      nextContentPosition = fixRangeEnd;
    }
    CharacterPosition eof = oldContent.isMissingNewlineAtEnd() ?
        new CharacterPosition(oldContent.size() - 1, oldContent.getString(oldContent.size() - 1).length())
        : new CharacterPosition(oldContent.size(), 0);
    if(nextContentPosition.before(eof)) {
      collector.addOldContent(nextContentPosition, eof);
    }

    return new FixResult(new ArrayList<>(collector.edits), oldContent);
  }

  static class CharacterPosition {
    int row;
    int column;
    public CharacterPosition(int row, int column) {
      this.row = row;
      this.column = column;
    }
    public boolean before(CharacterPosition other) {
      return (row < other.row) ||
          (row == other.row && column < other.column);
    }
  }

  private static class ContentCollector {
    private Text oldContent;
    private StringBuilder newContent;
    private int newContentRow;
    private int newContentColumn;
    List<Edit> edits;
    Comment.Range oldContentRange;
    Comment.Range newContentRange;


    ContentCollector(Text oldContent) {
      this.edits = new ArrayList<>();

      this.oldContent = oldContent;
      this.newContent = new StringBuilder();
      newContentRow = 1;
    }
    public void addOldContent(CharacterPosition from, CharacterPosition to) {
      int numOfCharsBefore = newContent.length();
      String firstString = oldContent.getString(from.row - 1, from.row, false);
      int lastRow = to.column == 0 ? to.row - 1 : to.row;
      if(lastRow > from.row) {
        newContent.append(firstString.substring(from.column));
        newContentRow++;
        for (int i = from.row + 1; i < lastRow; i++) {
          newContent.append(oldContent.getString(i - 1, i, false));
          newContentRow++;
        }
        String lastString = oldContent.getString(lastRow - 1, lastRow, false);
        if(to.column > 0) {
          newContent.append(lastString.substring(0, to.column));
          newContentColumn = to.column;
        } else {
          newContent.append(lastString);
          newContentRow++;
          newContentColumn = 0;
        }
      } else {
        //lastRow == from.row -- add check!!!
        newContent.append(firstString.substring(from.column, to.column));
        newContentColumn += to.column - from.column;
      }
      int numOfAddedChars = newContent.length() - numOfCharsBefore;
    }
    public void addReplacement(CharacterPosition oldContentStart, CharacterPosition oldContentEnd, String replacement) {
      int firstRowIndex = newContentRow;
      newContent.append(replacement);
      int lineStart = 0;
      while(true) {
        int lineEnd = replacement.indexOf('\n', lineStart);
        if (lineEnd < 0) {
          break;
        }
        newContentRow++;
        newContentColumn = 0;
        lineStart = lineEnd + 1;
      }
      newContentColumn += replacement.length() - lineStart;

      edits.add(new Edit(oldContentStart.row, oldContentEnd.row, firstRowIndex, newContentRow + 1));
    }
  }

  public static class FixResult {
    public final List<Edit> edits;
    public final Text text;
    public FixResult(List<Edit> edits, Text text) {
      this.edits = edits;
      this.text = text;
    }
  }

}
