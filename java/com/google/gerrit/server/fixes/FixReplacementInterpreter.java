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
import static java.util.stream.Collectors.toList;
import static java.nio.charset.StandardCharsets.UTF_8;

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
    for (FixReplacement fixReplacement : sortedReplacements) {
      collector.addReplacement(fixReplacement);
    }
    collector.finishExistingEdit();

    return new FixResult(new ArrayList<>(collector.edits), collector.getNewText());
  }


  private static class ContentCollector {
    private static class FixRegion {
      int startSrcLine;
      int startDstLine;
      int startSrcPos;
      int startDstPos;
      List<Edit> internalEdits;
      public FixRegion() {
        this.internalEdits = new ArrayList<>();
      }
    }

    private final ContentProcessor contentProcessor;
    final List<Edit> edits;
    FixRegion currentRegion;


    ContentCollector(Text src) {
      this.contentProcessor = new ContentProcessor(src);
      this.edits = new ArrayList<>();
    }

    void addReplacement(FixReplacement replacement) {
      if(startNewEdit(replacement)) {
        finishExistingEdit();
      }
      processSrcContent(replacement.range.startLine - 1, replacement.range.startChar, true);
      processReplacement(replacement);
    }

    Text getNewText() {
      return new Text(contentProcessor.sb.toString().getBytes(UTF_8));
    }

    void finishExistingEdit() {
      if(contentProcessor.srcPosition.hasUnfinishedLine()) {
        contentProcessor.processToEndOfLine(true);
      }
      if(currentRegion != null) {
        ReplaceEdit edit = new ReplaceEdit(currentRegion.startSrcLine,
            contentProcessor.srcPosition.line,
            currentRegion.startDstLine, contentProcessor.dstPosition.line,
            currentRegion.internalEdits);
        currentRegion = null;
        edits.add(edit);
      }
    }

    private boolean startNewEdit(FixReplacement replacement) {
      if (currentRegion == null) {
        return true;
      }
      return replacement.range.startLine <= contentProcessor.srcPosition.line;
    }

    private void processSrcContent(int toLine, int toColumn, boolean append) {
      //toLine >= currentSrcLineIndex

      if(toLine == contentProcessor.srcPosition.line) {
        contentProcessor.processLineToColumn(toColumn, append);
      } else {
        contentProcessor.processToEndOfLine(append);
        contentProcessor.processMultiline(toLine , append);
        contentProcessor.processLineToColumn(toColumn, append);
      }
    }

    private void processReplacement(FixReplacement fix) {
      if(currentRegion == null) {
        currentRegion = new FixRegion();
        currentRegion.startSrcLine = contentProcessor.srcPosition.line;
        currentRegion.startSrcPos = contentProcessor.srcPosition.getLineStartPos();
        currentRegion.startDstLine = contentProcessor.dstPosition.line;
        currentRegion.startDstPos = contentProcessor.dstPosition.getLineStartPos();
      }
      int srcStartPos = contentProcessor.srcPosition.pos;
      int dstStartPos = contentProcessor.dstPosition.pos;
      contentProcessor.appendReplacement(fix.replacement);
      processSrcContent(fix.range.endLine-1, fix.range.endChar, false);

      currentRegion.internalEdits.add(new Edit(srcStartPos - currentRegion.startSrcPos, contentProcessor.srcPosition.pos - currentRegion.startSrcPos, dstStartPos - currentRegion.startDstPos, contentProcessor.dstPosition.pos - currentRegion.startDstPos));
    }
  }

  private static class ContentProcessor {
    static class ContentPosition {
      int line;
      int column;
      int pos;

      void appendMultilineContent(int lineCount, int charCount) {
        line += lineCount;
        column = 0;
        pos += charCount;
      }

      void appendLineWithEOLMark(int charCount) {
        pos += charCount;
        line++;
        column = 0;
      }

      void appendStringWithoutEOLMark(int charCount) {
        pos += charCount;
        column += charCount;
      }

      boolean hasUnfinishedLine() {
        return column != 0;
      }

      int getLineStartPos() {
        return pos - column;
      }

    }
    private final StringBuilder sb;
    final ContentPosition srcPosition;
    final ContentPosition dstPosition;
    String currentSrcLine;
    Text src;
    ContentProcessor(Text src) {
      this.src = src;
      sb = new StringBuilder(src.size());
      srcPosition = new ContentPosition();
      dstPosition = new ContentPosition();

    }

    public void processMultiline(int toLine, boolean append) {
      if(toLine < srcPosition.line) {
        return;
      }
      int fromLine = srcPosition.line;
      String lines = src.getString(fromLine, toLine, false);
      int lineCount = toLine - fromLine;
      int charCount = lines.length();
      srcPosition.appendMultilineContent(lineCount, charCount);

      if(append) {
        sb.append(lines);
        dstPosition.appendMultilineContent(lineCount, charCount);
      }
      currentSrcLine = null;
    }
    public void processToEndOfLine(boolean append) {
      String srcLine = getCurrentSrcLine();
      int from = srcPosition.column;
      int charCount = srcLine.length() - from;
      srcPosition.appendLineWithEOLMark(charCount);
      if(append) {
        sb.append(srcLine, from, srcLine.length());
        dstPosition.appendLineWithEOLMark(charCount);
      }
      currentSrcLine = null;
    }
    public void processLineToColumn(int to, boolean append) {
      String srcLine = getCurrentSrcLine();
      int from = srcPosition.column;
      int charCount = to - from;
      srcPosition.appendStringWithoutEOLMark(charCount);
      if(append) {
        sb.append(srcLine, from, to);
        dstPosition.appendStringWithoutEOLMark(charCount);
      }
    }
    public void appendReplacement(String replacement) {
      if(replacement.length() == 0) {
        return;
      }
      sb.append(replacement);
      int lastNewLinePos = -1;
      int newLineMarkCount = 0;
      while(true) {
        int index = replacement.indexOf('\n', lastNewLinePos + 1);
        if(index < 0) {
          break;
        }
        lastNewLinePos = index;
        newLineMarkCount++;
      }
      if(newLineMarkCount > 0) {
        dstPosition.appendMultilineContent(newLineMarkCount, lastNewLinePos + 1);
      }
      dstPosition.appendStringWithoutEOLMark(replacement.length() - lastNewLinePos);
    }

    private String getCurrentSrcLine() {
      if(currentSrcLine == null) {
        currentSrcLine = src.getString(srcPosition.line, srcPosition.line + 1, false);
      }
      return currentSrcLine;
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
