// Copyright (C) 2019 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.server.patch.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

/**
 * Produces final version of an input content with all fixes applied together with list of edits.
 */
public class FixCalculator {
  private static final Comparator<FixReplacement> ASC_RANGE_FIX_REPLACEMENT_COMPARATOR =
      Comparator.comparing(fixReplacement -> fixReplacement.range);

  private FixCalculator() {}

  /**
   * Returns a result of applying fixes to an original content.
   *
   * @param originalContent is a text to which fixes must be applied
   * @param fixReplacements is a list of fixes to be applied
   * @throws ResourceConflictException if the fixReplacements contains invalid data (for example, if
   *     an item points to an invalid range or if some ranges are intersected).
   */
  public static String getNewFileContent(
      String originalContent, List<FixReplacement> fixReplacements)
      throws ResourceConflictException {
    FixResult fixResult = calculateFix(new Text(originalContent.getBytes(UTF_8)), fixReplacements);
    return fixResult.text.getString(0, fixResult.text.size(), false);
  }

  /**
   * Returns a result of applying fixes to an original content and list of applied edits.
   *
   * @param originalText is a text to which fixes must be applied
   * @param fixReplacements is a list of fixes to be applied
   * @return {@link FixResult}
   * @throws ResourceConflictException if the fixReplacements contains invalid data (for example, if
   *     an item points to an invalid range or if some ranges are intersected).
   */
  public static FixResult calculateFix(Text originalText, List<FixReplacement> fixReplacements)
      throws ResourceConflictException {
    List<FixReplacement> sortedReplacements = new ArrayList<>(fixReplacements);
    sortedReplacements.sort(ASC_RANGE_FIX_REPLACEMENT_COMPARATOR);
    if (!sortedReplacements.isEmpty() && sortedReplacements.get(0).range.startLine <= 0) {
      throw new ResourceConflictException(
          String.format(
              "Cannot calculate fix replacement for range %s",
              toString(sortedReplacements.get(0).range)));
    }
    ContentBuilder builder = new ContentBuilder(originalText);
    for (FixReplacement fixReplacement : sortedReplacements) {
      try {
        builder.addReplacement(fixReplacement);
      } catch (IndexOutOfBoundsException e) {
        throw new ResourceConflictException(
            String.format(
                "Cannot calculate fix replacement for range %s", toString(fixReplacement.range)),
            e);
      }
    }
    return builder.build();
  }

  private static String toString(Comment.Range range) {
    return String.format(
        "(%s:%s - %s:%s)", range.startLine, range.startChar, range.endLine, range.endChar);
  }
  /*
  Algorithm:
  Input:
    Original text (aka srcText)
    Sorted list of replacements in ascending order, where each replacement, where
    each replacement has:
        srcRange - part of the original text to be
                   replaced, inserted or deleted (see {@link Comment.Range} for details)
        replacement - text to be set instead of srcRange
    Replacement ranges must not intersect.

  Output:
    Final text (aka finalText)
    List of Edit, where each Edit is an instance of {@link ReplaceEdit}
      Each ReplaceEdit cover one or more lines in the original text
      Each ReplaceEdit contains one or more Edit for intraline edits
    See {@link ReplaceEdit} and {@link Edit} for details.
  *
  Note: The algorithm implemented in this way to avoid string.replace operations. It has complexity
  O(len(replacements) + max(len(originalText), len(finalText)) )

  Main steps:
  - set srcPos to start of the original text. It is like a cursor position in the original text.
  - set dstPos to start of the final text.  It is like a cursor position in the final text.
  - the finalText initially empty

  - for each replacement:
       - append text between a previous and a current replacement to the finalText
           (because replacements were sorted, this part of text can't be changed by
             following replacements). I.e. append substring of srcText between srcPos
             and replacement.srcRange.start to the finalText
           Update srcPos and dstPos - set them at the end of appended text
           (i.e. srcPos points to the position before replacement.srcRange.start,
            dstPos points to the position where replacement.text should be inserted)
       - set dstReplacementStart = dstPos
       - append replacement.text to the finalText.
           Update srcPos and dstPos accordingly (i.e. srcPos points to the position after
           replacement.srcRange, dstPos points to the position in the finalText after
           the appended replacement.text).
       - set dstReplacementEnd = dstPos
       - dstRange = (dstReplacementStart, dstReplacementEnd) - is the range in the finalText.
       - srcRange = (replacement.Start, replacement.End) -  is the range in the original text *

       - If previously created ReplaceEdit ends on the same or previous line as srcRange.startLine,
           then intraline edit is added to it (and ReplaceEdit endLine must be updated if needed);
           srcRange and dstRange together is used to calculate intraline Edit
         otherwise
          create new ReplaceEdit and add intraline Edit to it
          srcRange and dstRange together is used to calculate intraline Edit

  - append text after the last replacements,
      i.e. add part of srcText after srcPos to the finalText

  - Return the finalText and all created ReplaceEdits

  Implementation notes:
  1) The intraline Edits inside ReplaceEdit stores positions relative to ReplaceEdit start.
  2) srcPos and dstPos tracks current position as 3 numbers:
  - line number
  - column number
  - textPos - absolute position from the start of the text. The textPos is used to calculate
  relative positions of Edit inside ReplaceEdit
     */
  private static class ContentBuilder {
    private static class FixRegion {
      int startSrcLine;
      int startDstLine;
      int startSrcPos;
      int startDstPos;
      List<Edit> internalEdits;

      FixRegion() {
        this.internalEdits = new ArrayList<>();
      }
    }

    private final ContentProcessor contentProcessor;
    final ImmutableList.Builder<Edit> edits;
    FixRegion currentRegion;

    ContentBuilder(Text src) {
      this.contentProcessor = new ContentProcessor(src);
      this.edits = new ImmutableList.Builder<>();
    }

    void addReplacement(FixReplacement replacement) {
      if (shouldStartNewEdit(replacement)) {
        finishExistingEdit();
      }
      // processSrcContent expects that line number is 0-based,
      // but replacement.range.startLine is 1-based, so subtract 1
      processSrcContent(replacement.range.startLine - 1, replacement.range.startChar, true);
      processReplacement(replacement);
    }

    Text getNewText() {
      return new Text(contentProcessor.sb.toString().getBytes(UTF_8));
    }

    void finish() {
      finishExistingEdit();
      if (contentProcessor.hasMoreLines()) {
        contentProcessor.appendLinesToEndOfContent();
      }
    }

    public FixResult build() {
      finish();
      return new FixResult(edits.build(), this.getNewText());
    }

    private void finishExistingEdit() {
      if (contentProcessor.srcPosition.column > 0 || contentProcessor.dstPosition.column > 0) {
        contentProcessor.processToEndOfLine(true);
      }
      if (currentRegion != null) {
        int endSrc = contentProcessor.srcPosition.line;
        if (contentProcessor.srcPosition.column > 0) {
          endSrc++;
        }
        int endDst = contentProcessor.dstPosition.line;
        if (contentProcessor.dstPosition.column > 0) {
          endDst++;
        }
        ReplaceEdit edit =
            new ReplaceEdit(
                currentRegion.startSrcLine,
                endSrc,
                currentRegion.startDstLine,
                endDst,
                currentRegion.internalEdits);
        currentRegion = null;
        edits.add(edit);
      }
    }

    private boolean shouldStartNewEdit(FixReplacement replacement) {
      if (currentRegion == null) {
        return true;
      }
      // New edit must be started if there is at least one unchanged line after the last edit
      // Subtract 1 from replacement.range.startLine because it is a 1-based line number,
      // and contentProcessor.srcPosition.line is a 0-based line number
      return replacement.range.startLine - 1 > contentProcessor.srcPosition.line + 1;
    }

    private void processSrcContent(int toLine, int toColumn, boolean append)
        throws IndexOutOfBoundsException {
      // toLine >= currentSrcLineIndex
      if (toLine == contentProcessor.srcPosition.line) {
        contentProcessor.processLineToColumn(toColumn, append);
      } else {
        contentProcessor.processToEndOfLine(append);
        contentProcessor.processMultiline(toLine, append);
        contentProcessor.processLineToColumn(toColumn, append);
      }
    }

    private void processReplacement(FixReplacement fix) {
      if (currentRegion == null) {
        currentRegion = new FixRegion();
        currentRegion.startSrcLine = contentProcessor.srcPosition.line;
        currentRegion.startSrcPos = contentProcessor.srcPosition.getLineStartPos();
        currentRegion.startDstLine = contentProcessor.dstPosition.line;
        currentRegion.startDstPos = contentProcessor.dstPosition.getLineStartPos();
      }
      int srcStartPos = contentProcessor.srcPosition.textPos;
      int dstStartPos = contentProcessor.dstPosition.textPos;
      contentProcessor.appendReplacement(fix.replacement);
      processSrcContent(fix.range.endLine - 1, fix.range.endChar, false);

      currentRegion.internalEdits.add(
          new Edit(
              srcStartPos - currentRegion.startSrcPos,
              contentProcessor.srcPosition.textPos - currentRegion.startSrcPos,
              dstStartPos - currentRegion.startDstPos,
              contentProcessor.dstPosition.textPos - currentRegion.startDstPos));
    }
  }

  private static class ContentProcessor {
    static class ContentPosition {
      int line;
      int column;
      int textPos;

      void appendMultilineContent(int lineCount, int charCount) {
        line += lineCount;
        column = 0;
        textPos += charCount;
      }

      void appendLineEndedWithEOLMark(int charCount) {
        textPos += charCount;
        line++;
        column = 0;
      }

      void appendStringWithoutEOLMark(int charCount) {
        textPos += charCount;
        column += charCount;
      }

      int getLineStartPos() {
        return textPos - column;
      }
    }

    private final StringBuilder sb;
    final ContentPosition srcPosition;
    final ContentPosition dstPosition;
    String currentSrcLine;
    Text src;
    boolean endOfSource;

    ContentProcessor(Text src) {
      this.src = src;
      sb = new StringBuilder(src.size());
      srcPosition = new ContentPosition();
      dstPosition = new ContentPosition();
      endOfSource = src.size() == 0;
    }

    void processMultiline(int toLine, boolean append) {
      if (endOfSource || toLine <= srcPosition.line) {
        return;
      }
      int fromLine = srcPosition.line;
      String lines = src.getString(fromLine, toLine, false);
      int lineCount = toLine - fromLine;
      int charCount = lines.length();
      srcPosition.appendMultilineContent(lineCount, charCount);

      if (append) {
        sb.append(lines);
        dstPosition.appendMultilineContent(lineCount, charCount);
      }
      currentSrcLine = null;
      endOfSource = srcPosition.line >= src.size();
    }

    void processToEndOfLine(boolean append) {
      if (endOfSource) {
        return;
      }
      String srcLine = getCurrentSrcLine();
      int from = srcPosition.column;
      int charCount = srcLine.length() - from;
      boolean lastLineNoEOLMark = srcPosition.line >= src.size() - 1 && src.isMissingNewlineAtEnd();
      if (!lastLineNoEOLMark) {
        srcPosition.appendLineEndedWithEOLMark(charCount);
        endOfSource = srcPosition.line >= src.size();
      } else {
        srcPosition.appendStringWithoutEOLMark(charCount);
        endOfSource = true;
      }
      if (append) {
        sb.append(srcLine, from, srcLine.length());
        if (!lastLineNoEOLMark) {
          dstPosition.appendLineEndedWithEOLMark(charCount);
        } else {
          dstPosition.appendStringWithoutEOLMark(charCount);
        }
      }
      currentSrcLine = null;
    }

    void processLineToColumn(int to, boolean append) throws IndexOutOfBoundsException {
      if (to == 0) {
        return;
      }
      String srcLine = getCurrentSrcLine();
      if (to > srcLine.length()) {
        throw new IndexOutOfBoundsException("Parameter to is out of string");
      } else if (to == srcLine.length()) {
        if (srcPosition.line < src.size() - 1 || !src.isMissingNewlineAtEnd()) {
          throw new IndexOutOfBoundsException("The processLineToColumn shouldn't add end of line");
        }
      }
      int from = srcPosition.column;
      int charCount = to - from;
      srcPosition.appendStringWithoutEOLMark(charCount);
      if (append) {
        sb.append(srcLine, from, to);
        dstPosition.appendStringWithoutEOLMark(charCount);
      }
    }

    void appendLinesToEndOfContent() {
      processMultiline(src.size(), true);
    }

    void appendReplacement(String replacement) {
      if (replacement.length() == 0) {
        return;
      }
      sb.append(replacement);
      int lastNewLinePos = -1;
      int newLineMarkCount = 0;
      while (true) {
        int index = replacement.indexOf('\n', lastNewLinePos + 1);
        if (index < 0) {
          break;
        }
        lastNewLinePos = index;
        newLineMarkCount++;
      }
      if (newLineMarkCount > 0) {
        dstPosition.appendMultilineContent(newLineMarkCount, lastNewLinePos + 1);
      }
      dstPosition.appendStringWithoutEOLMark(replacement.length() - lastNewLinePos - 1);
    }

    boolean hasMoreLines() {
      return !endOfSource;
    }

    private String getCurrentSrcLine() {
      if (currentSrcLine == null) {
        currentSrcLine = src.getString(srcPosition.line, srcPosition.line + 1, false);
      }
      return currentSrcLine;
    }
  }

  /** The result of applying fix to a file content */
  public static class FixResult {
    /** List of edits to transform an original text to a final text (with all fixes applied) */
    public final ImmutableList<Edit> edits;
    /** Final text with all applied fixes */
    public final Text text;

    FixResult(ImmutableList<Edit> edits, Text text) {
      this.edits = edits;
      this.text = text;
    }
  }
}
