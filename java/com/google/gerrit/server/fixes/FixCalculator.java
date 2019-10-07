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

import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.FixReplacement;
import com.google.gerrit.server.patch.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

public class FixCalculator {
  private static final Comparator<FixReplacement> ASC_RANGE_FIX_REPLACEMENT_COMPARATOR =
      Comparator.comparing(fixReplacement -> fixReplacement.range);

  public static FixResult calculateFix(Text oldContent, List<FixReplacement> fixReplacements)
      throws ResourceConflictException {
    List<FixReplacement> sortedReplacements = new ArrayList<>(fixReplacements);
    sortedReplacements.sort(ASC_RANGE_FIX_REPLACEMENT_COMPARATOR);
    ContentCollector collector = new ContentCollector(oldContent);
    if (!sortedReplacements.isEmpty() && sortedReplacements.get(0).range.startLine == 0) {
      throw new ResourceConflictException(
          String.format(
              "Cannot calculate fix replacement for range %s",
              toString(sortedReplacements.get(0).range)));
    }
    for (FixReplacement fixReplacement : sortedReplacements) {
      try {
        collector.addReplacement(fixReplacement);
      } catch (IndexOutOfBoundsException e) {
        throw new ResourceConflictException(
            String.format(
                "Cannot calculate fix replacement for range %s", toString(fixReplacement.range)),
            e);
      }
    }
    collector.finish();
    return new FixResult(new ArrayList<>(collector.edits), collector.getNewText());
  }

  private static String toString(Comment.Range range) {
    return String.format(
        "(%s:%s - %s:%s)", range.startLine, range.startChar, range.endLine, range.endChar);
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
      if (startNewEdit(replacement)) {
        finishExistingEdit();
      }
      processSrcContent(replacement.range.startLine - 1, replacement.range.startChar, true);
      processReplacement(replacement);
    }

    Text getNewText() {
      return new Text(contentProcessor.sb.toString().getBytes(UTF_8));
    }

    void finish() {
      finishExistingEdit();
      if (contentProcessor.hasMoreLines()) {
        contentProcessor.processLinesToEndOfContent(true);
      }
    }

    private void finishExistingEdit() {
      if (contentProcessor.hasMoreContentInCurrentLine()) {
        contentProcessor.processToEndOfLine(true);
      }
      if (currentRegion != null) {
        ReplaceEdit edit =
            new ReplaceEdit(
                currentRegion.startSrcLine,
                contentProcessor.srcPosition.line,
                currentRegion.startDstLine,
                contentProcessor.dstPosition.line,
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
      int srcStartPos = contentProcessor.srcPosition.pos;
      int dstStartPos = contentProcessor.dstPosition.pos;
      contentProcessor.appendReplacement(fix.replacement);
      processSrcContent(fix.range.endLine - 1, fix.range.endChar, false);

      currentRegion.internalEdits.add(
          new Edit(
              srcStartPos - currentRegion.startSrcPos,
              contentProcessor.srcPosition.pos - currentRegion.startSrcPos,
              dstStartPos - currentRegion.startDstPos,
              contentProcessor.dstPosition.pos - currentRegion.startDstPos));
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
      if (toLine <= srcPosition.line) {
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
    }

    public void processToEndOfLine(boolean append) {
      String srcLine = getCurrentSrcLine();
      int from = srcPosition.column;
      int charCount = srcLine.length() - from;
      srcPosition.appendLineWithEOLMark(charCount);
      if (append) {
        sb.append(srcLine, from, srcLine.length());
        dstPosition.appendLineWithEOLMark(charCount);
      }
      currentSrcLine = null;
    }

    public void processLineToColumn(int to, boolean append) throws IndexOutOfBoundsException {
      if (to == 0) {
        return;
      }
      String srcLine = getCurrentSrcLine();
      if (to > srcLine.length()) {
        throw new IndexOutOfBoundsException("Parameter to is out of string");
      } else if (to == srcLine.length()) {
        if (src.isMissingNewlineAtEnd()) {
          processToEndOfLine(append);
          return;
        }
        throw new IndexOutOfBoundsException("The processLineToColumn shouldn't add end of line");
      }
      int from = srcPosition.column;
      int charCount = to - from;
      srcPosition.appendStringWithoutEOLMark(charCount);
      if (append) {
        sb.append(srcLine, from, to);
        dstPosition.appendStringWithoutEOLMark(charCount);
      }
    }

    public void processLinesToEndOfContent(boolean append) {
      processMultiline(src.size(), append);
    }

    public void appendReplacement(String replacement) {
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
      dstPosition.appendStringWithoutEOLMark(replacement.length() - lastNewLinePos);
    }

    public boolean hasMoreContentInCurrentLine() {
      return srcPosition.column != 0;
    }

    public boolean hasMoreLines() {
      return srcPosition.line < src.size();
    }

    private String getCurrentSrcLine() {
      if (currentSrcLine == null) {
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
