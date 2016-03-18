// Copyright (C) 2009 The Android Open Source Project
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
//

package com.google.gerrit.server.patch;

import com.google.common.base.Throwables;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

class IntraLineLoader implements Callable<IntraLineDiff> {
  static final Logger log = LoggerFactory.getLogger(IntraLineLoader.class);

  static interface Factory {
    IntraLineLoader create(IntraLineDiffKey key, IntraLineDiffArgs args);
  }

  private static final Pattern BLANK_LINE_RE = Pattern
      .compile("^[ \\t]*(|[{}]|/\\*\\*?|\\*)[ \\t]*$");

  private static final Pattern CONTROL_BLOCK_START_RE = Pattern
      .compile("[{:][ \\t]*$");

  private final ExecutorService diffExecutor;
  private final long timeoutMillis;
  private final IntraLineDiffKey key;
  private final IntraLineDiffArgs args;

  @AssistedInject
  IntraLineLoader(@DiffExecutor ExecutorService diffExecutor,
      @GerritServerConfig Config cfg,
      @Assisted IntraLineDiffKey key,
      @Assisted IntraLineDiffArgs args) {
    this.diffExecutor = diffExecutor;
    timeoutMillis =
        ConfigUtil.getTimeUnit(cfg, "cache", PatchListCacheImpl.INTRA_NAME,
            "timeout", TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
    this.key = key;
    this.args = args;
  }

  @Override
  public IntraLineDiff call() throws Exception {
    Future<IntraLineDiff> result = diffExecutor.submit(
        new Callable<IntraLineDiff>() {
          @Override
          public IntraLineDiff call() throws Exception {
            return IntraLineLoader.compute(args.aText(), args.bText(),
                args.edits());
          }
        });
    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      log.warn(timeoutMillis + " ms timeout reached for IntraLineDiff"
          + " in project " + args.project()
          + " on commit " + args.commit().name()
          + " for path " + args.path()
          + " comparing " + key.getBlobA().name()
          + ".." + key.getBlobB().name());
      result.cancel(true);
      return new IntraLineDiff(IntraLineDiff.Status.TIMEOUT);
    } catch (ExecutionException e) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      Throwables.propagateIfInstanceOf(e.getCause(), Exception.class);
      throw new Exception(e.getMessage(), e.getCause());
    }
  }

  static IntraLineDiff compute(Text aText, Text bText, List<Edit> edits)
      throws Exception {
    combineLineEdits(edits, aText, bText);

    for (int i = 0; i < edits.size(); i++) {
      Edit e = edits.get(i);

      if (e.getType() == Edit.Type.REPLACE) {
        CharText a = new CharText(aText, e.getBeginA(), e.getEndA());
        CharText b = new CharText(bText, e.getBeginB(), e.getEndB());
        CharTextComparator cmp = new CharTextComparator();

        List<Edit> wordEdits = MyersDiff.INSTANCE.diff(cmp, a, b);

        Edit prev = null;
        for (int j = 0; j < wordEdits.size(); j++) {
          Edit edit = wordEdits.get(j);

          edit = shiftEdit(a, b, edit);

          Edit combined = combineCloseEdits(a, b, prev, edit);
          if (combined != null) {
            edit = combined;
            wordEdits.remove(j-1);
            j--;
          }

          edit = cmp.reduceCommonStartEnd(a, b, edit);
          edit = combineEditWithLF(a, b, edit);

          wordEdits.set(j, edit);
          prev = edit;
        }

        edits.set(i, new ReplaceEdit(e, wordEdits));
      }
    }

    return new IntraLineDiff(edits);
  }

  private static void combineLineEdits(List<Edit> edits, Text a, Text b) {
    for (int j = 0; j < edits.size() - 1;) {
      Edit c = edits.get(j);
      Edit n = edits.get(j + 1);

      // Combine edits that are really close together. Right now our rule
      // is, coalesce two line edits which are only one line apart if that
      // common context line is either a "pointless line", or is identical
      // on both sides and starts a new block of code. These are mostly
      // block reindents to add or remove control flow operators.
      //
      final int ad = n.getBeginA() - c.getEndA();
      final int bd = n.getBeginB() - c.getEndB();
      if ((1 <= ad && isBlankLineGap(a, c.getEndA(), n.getBeginA()))
          || (1 <= bd && isBlankLineGap(b, c.getEndB(), n.getBeginB()))
          || (ad == 1 && bd == 1 && isControlBlockStart(a, c.getEndA()))) {
        int ab = c.getBeginA();
        int ae = n.getEndA();

        int bb = c.getBeginB();
        int be = n.getEndB();

        edits.set(j, new Edit(ab, ae, bb, be));
        edits.remove(j + 1);
        continue;
      }

      j++;
    }
  }

  private static boolean isBlankLineGap(Text a, int b, int e) {
    for (; b < e; b++) {
      if (!BLANK_LINE_RE.matcher(a.getString(b)).matches()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isControlBlockStart(Text a, int idx) {
    return CONTROL_BLOCK_START_RE.matcher(a.getString(idx)).find();
  }

  // Combine edits that are really close together. If they are
  // just a few characters apart we tend to get better results
  // by joining them together and taking the whole span.
  private static Edit combineCloseEdits(CharText a, CharText b, Edit prev, Edit next) {
    if (prev == null) {
      return null;
    }
    if ((next.getBeginA() - prev.getEndA() <= 5 || next.getBeginB() - prev.getEndB() <= 5) &&
        !a.contains(prev.getEndA(), next.getBeginA(), '\n') &&
        !b.contains(prev.getEndB(), next.getBeginB(), '\n')) {
      return new Edit(prev.getBeginA(), next.getEndA(), prev.getBeginB(), next.getEndB());
    }
    return null;
  }

  // If most of a line was modified except the LF was common, make
  // the LF part of the modification region. This is easier to read.
  private static Edit combineEditWithLF(CharText a, CharText b, Edit edit) {
    if (edit.getLengthA() > 0 && edit.getLengthB() > 0 &&
        edit.getEndA() < a.size() && edit.getEndB() < b.size() &&
        a.isLineStart(edit.getBeginA()) && b.isLineStart(edit.getBeginB()) &&
        a.charAt(edit.getEndA()-1) != '\n' && a.charAt(edit.getEndA()) == '\n' &&
        b.charAt(edit.getEndB()-1) != '\n' && b.charAt(edit.getEndB()) == '\n') {
      // we can extend into the LF:
      edit.extendA();
      edit.extendB();
    }
    return edit;
  }

  // The leading part of an edit and its trailing part in the same
  // text might be identical. Slide down that edit and use the tail
  // rather than the leading bit.
  private static Edit shiftEdit(CharText a, CharText b, Edit edit) {
    int beginA = edit.getBeginA();
    int endA = edit.getEndA();
    int beginB = edit.getBeginB();
    int endB = edit.getEndB();
    int shifted = 0;

    // first try to shift left until the start of the line
    while (canShiftLeft(a, beginA, endA) && canShiftLeft(b, beginB, endB)) {
      beginA--;
      endA--;
      beginB--;
      endB--;
      shifted--;
    }

    // if we cannot reach the start, then shift right as far as possible
    if (!a.isLineStart(beginA) && !b.isLineStart(beginB)) {
      while (canShiftRight(a, beginA, endA) && canShiftRight(b, beginB, endB)) {
        beginA++;
        endA++;
        beginB++;
        endB++;
        shifted++;
      }
    }

    if (shifted != 0) {
      edit = new Edit(beginA, endA, beginB, endB);
    }

    return edit;
  }

  private static boolean canShiftLeft(CharText a, int begin, int end) {
    if (begin == 0 || a.charAt(begin-1) == '\n') {
      return false;
    }
    return begin == end || a.charAt(begin-1) == a.charAt(end-1);
  }

  private static boolean canShiftRight(CharText a, int begin, int end) {
    if (end >= a.size() || a.charAt(end) == '\n') {
      return false;
    }
    return begin == end || a.charAt(begin) == a.charAt(end);
  }
}
