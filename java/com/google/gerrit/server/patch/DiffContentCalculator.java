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

package com.google.gerrit.server.patch;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.prettify.common.EditHunk;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.prettify.common.SparseFileContentBuilder;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.diff.Edit;

/** Collects all lines and their content to be displayed in diff view. */
class DiffContentCalculator {
  private final DiffPreferencesInfo diffPrefs;

  DiffContentCalculator(DiffPreferencesInfo diffPrefs) {
    this.diffPrefs = diffPrefs;
  }

  /**
   * Gather information necessary to display line-by-line difference between 2 texts.
   *
   * <p>The method returns instance of {@link DiffCalculatorResult} with the following data:
   *
   * <ul>
   *   <li>All changed lines
   *   <li>Additional lines to be displayed above and below the changed lines
   *   <li>All changed and unchanged lines with comments
   *   <li>Additional lines to be displayed above and below lines with commentsEdits with special
   *       "fake" edits for unchanged lines with comments
   * </ul>
   *
   * <p>More details can be found in {@link DiffCalculatorResult}.
   *
   * @param srcA Original text content
   * @param srcB New text content
   * @param edits List of edits which was applied to srcA to produce srcB
   * @return an instance of {@link DiffCalculatorResult}.
   */
  DiffCalculatorResult calculateDiffContent(
      TextSource srcA, TextSource srcB, ImmutableList<Edit> edits) {
    if (srcA.src == srcB.src && edits.isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      SparseFileContentBuilder diffA = new SparseFileContentBuilder(srcA.size());
      for (int i = 0; i < srcA.size(); i++) {
        srcA.copyLineTo(diffA, i);
      }
      DiffContent diffContent =
          new DiffContent(diffA.build(), SparseFileContent.create(ImmutableList.of(), srcB.size()));
      Edit emptyEdit = new Edit(srcA.size(), srcA.size());
      return new DiffCalculatorResult(diffContent, ImmutableList.of(emptyEdit));
    }
    ImmutableList<Edit> sortedEdits = correctForDifferencesInNewlineAtEnd(srcA, srcB, edits);

    DiffContent diffContent =
        packContent(srcA, srcB, diffPrefs.ignoreWhitespace != Whitespace.IGNORE_NONE, sortedEdits);
    return new DiffCalculatorResult(diffContent, sortedEdits);
  }

  private ImmutableList<Edit> correctForDifferencesInNewlineAtEnd(
      TextSource a, TextSource b, ImmutableList<Edit> edits) {
    // a.src.size() is the size ignoring a newline at the end whereas a.size() considers it.
    int aSize = a.src.size();
    int bSize = b.src.size();

    if (edits.isEmpty() && (aSize == 0 || bSize == 0)) {
      // The diff was requested for a file which was either added or deleted but which JGit doesn't
      // consider a file addition/deletion (e.g. requesting a diff for the old file name of a
      // renamed file looks like a deletion).
      return edits;
    }

    if (edits.isEmpty() && (aSize != bSize)) {
      // Only edits due to rebase were present. If we now added the edits for the newlines, the
      // code which later assembles the file contents would fail.
      return edits;
    }

    Optional<Edit> lastEdit = getLast(edits);
    if (isNewlineAtEndDeleted(a, b)) {
      Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndA() == aSize);

      if (lastLineEdit.isPresent()) {
        Edit edit = lastLineEdit.get();
        Edit updatedLastLineEdit =
            edit instanceof ReplaceEdit
                ? new ReplaceEdit(
                    edit.getBeginA(),
                    edit.getEndA() + 1,
                    edit.getBeginB(),
                    edit.getEndB(),
                    ((ReplaceEdit) edit).getInternalEdits())
                : new Edit(edit.getBeginA(), edit.getEndA() + 1, edit.getBeginB(), edit.getEndB());

        ImmutableList.Builder<Edit> newEditsBuilder =
            ImmutableList.builderWithExpectedSize(edits.size());
        return newEditsBuilder
            .addAll(edits.subList(0, edits.size() - 1))
            .add(updatedLastLineEdit)
            .build();
      }
      ImmutableList.Builder<Edit> newEditsBuilder =
          ImmutableList.builderWithExpectedSize(edits.size() + 1);
      Edit newlineEdit = new Edit(aSize, aSize + 1, bSize, bSize);
      return newEditsBuilder.addAll(edits).add(newlineEdit).build();

    } else if (isNewlineAtEndAdded(a, b)) {
      Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndB() == bSize);
      if (lastLineEdit.isPresent()) {
        Edit edit = lastLineEdit.get();
        Edit updatedLastLineEdit =
            edit instanceof ReplaceEdit
                ? new ReplaceEdit(
                    edit.getBeginA(),
                    edit.getEndA(),
                    edit.getBeginB(),
                    edit.getEndB() + 1,
                    ((ReplaceEdit) edit).getInternalEdits())
                : new Edit(edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB() + 1);

        ImmutableList.Builder<Edit> newEditsBuilder =
            ImmutableList.builderWithExpectedSize(edits.size());
        return newEditsBuilder
            .addAll(edits.subList(0, edits.size() - 1))
            .add(updatedLastLineEdit)
            .build();
      }
      ImmutableList.Builder<Edit> newEditsBuilder =
          ImmutableList.builderWithExpectedSize(edits.size() + 1);
      Edit newlineEdit = new Edit(aSize, aSize, bSize, bSize + 1);
      return newEditsBuilder.addAll(edits).add(newlineEdit).build();
    }
    return edits;
  }

  private static <T> Optional<T> getLast(List<T> list) {
    return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(list.size() - 1));
  }

  private boolean isNewlineAtEndDeleted(TextSource a, TextSource b) {
    return !a.src.isMissingNewlineAtEnd() && b.src.isMissingNewlineAtEnd();
  }

  private boolean isNewlineAtEndAdded(TextSource a, TextSource b) {
    return a.src.isMissingNewlineAtEnd() && !b.src.isMissingNewlineAtEnd();
  }

  private DiffContent packContent(
      TextSource a, TextSource b, boolean ignoredWhitespace, ImmutableList<Edit> edits) {
    SparseFileContentBuilder diffA = new SparseFileContentBuilder(a.size());
    SparseFileContentBuilder diffB = new SparseFileContentBuilder(b.size());
    if (!edits.isEmpty()) {
      EditHunk hunk = new EditHunk(edits, a.size(), b.size());
      while (hunk.next()) {
        if (hunk.isUnmodifiedLine()) {
          String lineA = a.getSourceLine(hunk.getCurA());
          diffA.addLine(hunk.getCurA(), lineA);

          if (ignoredWhitespace) {
            // If we ignored whitespace in some form, also get the line
            // from b when it does not exactly match the line from a.
            //
            String lineB = b.getSourceLine(hunk.getCurB());
            if (!lineA.equals(lineB)) {
              diffB.addLine(hunk.getCurB(), lineB);
            }
          }
          hunk.incBoth();
          continue;
        }

        if (hunk.isDeletedA()) {
          a.copyLineTo(diffA, hunk.getCurA());
          hunk.incA();
        }

        if (hunk.isInsertedB()) {
          b.copyLineTo(diffB, hunk.getCurB());
          hunk.incB();
        }
      }
    }
    return new DiffContent(diffA.build(), diffB.build());
  }

  /** Contains information to be displayed in line-by-line diff view. */
  static class DiffCalculatorResult {
    // This class is not @AutoValue, because Edit is mutable

    /** Lines to be displayed */
    final DiffContent diffContent;

    /** List of edits including "fake" edits for unchanged lines with comments. */
    final ImmutableList<Edit> edits;

    DiffCalculatorResult(DiffContent diffContent, ImmutableList<Edit> edits) {
      this.diffContent = diffContent;
      this.edits = edits;
    }
  }

  /** Lines to be displayed in line-by-line diff view. */
  static class DiffContent {
    /* All lines from the original text (i.e. srcA) to be displayed. */
    final SparseFileContent a;

    /**
     * All lines from the new text (i.e. srcB) which are different than in original text. Lines are:
     * a) All changed lines (i.e. if the content of the line was replaced with the new line) b) All
     * inserted lines Note, that deleted lines are added to the a and are not added to b
     */
    final SparseFileContent b;

    DiffContent(SparseFileContent a, SparseFileContent b) {
      this.a = a;
      this.b = b;
    }
  }

  static class TextSource {
    final Text src;

    TextSource(Text src) {
      this.src = src;
    }

    int size() {
      if (src == null) {
        return 0;
      }
      if (src.isMissingNewlineAtEnd()) {
        return src.size();
      }
      return src.size() + 1;
    }

    void copyLineTo(SparseFileContentBuilder target, int lineNumber) {
      target.addLine(lineNumber, getSourceLine(lineNumber));
    }

    private String getSourceLine(int lineNumber) {
      return lineNumber >= src.size() ? "" : src.getString(lineNumber);
    }
  }
}
