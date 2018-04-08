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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IntraLineLoader implements Callable<IntraLineDiff> {
  static final Logger log = LoggerFactory.getLogger(IntraLineLoader.class);

  interface Factory {
    IntraLineLoader create(IntraLineDiffKey key, IntraLineDiffArgs args);
  }

  private static final Pattern BLANK_LINE_RE =
      Pattern.compile("^[ \\t]*(|[{}]|/\\*\\*?|\\*)[ \\t]*$");

  private static final Pattern CONTROL_BLOCK_START_RE = Pattern.compile("[{:][ \\t]*$");

  private final ExecutorService diffExecutor;
  private final long timeoutMillis;
  private final IntraLineDiffKey key;
  private final IntraLineDiffArgs args;

  @Inject
  IntraLineLoader(
      @DiffExecutor ExecutorService diffExecutor,
      @GerritServerConfig Config cfg,
      @Assisted IntraLineDiffKey key,
      @Assisted IntraLineDiffArgs args) {
    this.diffExecutor = diffExecutor;
    timeoutMillis =
        ConfigUtil.getTimeUnit(
            cfg,
            "cache",
            PatchListCacheImpl.INTRA_NAME,
            "timeout",
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
    this.key = key;
    this.args = args;
  }

  @Override
  public IntraLineDiff call() throws Exception {
    Future<IntraLineDiff> result =
        diffExecutor.submit(
            () ->
                IntraLineLoader.compute(
                    args.aText(), args.bText(), args.edits(), args.editsDueToRebase()));
    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      log.warn(
          timeoutMillis
              + " ms timeout reached for IntraLineDiff"
              + " in project "
              + args.project()
              + " on commit "
              + args.commit().name()
              + " for path "
              + args.path()
              + " comparing "
              + key.getBlobA().name()
              + ".."
              + key.getBlobB().name());
      result.cancel(true);
      return new IntraLineDiff(IntraLineDiff.Status.TIMEOUT);
    } catch (ExecutionException e) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      Throwables.throwIfInstanceOf(e.getCause(), Exception.class);
      throw new Exception(e.getMessage(), e.getCause());
    }
  }

  static IntraLineDiff compute(
      Text aText,
      Text bText,
      ImmutableList<Edit> immutableEdits,
      ImmutableSet<Edit> immutableEditsDueToRebase) {
    List<Edit> edits = new ArrayList<>(immutableEdits);
    combineLineEdits(edits, immutableEditsDueToRebase, aText, bText);

    for (int i = 0; i < edits.size(); i++) {
      Edit e = edits.get(i);

      if (e.getType() == Edit.Type.REPLACE) {
        CharText a = new CharText(aText, e.getBeginA(), e.getEndA());
        CharText b = new CharText(bText, e.getBeginB(), e.getEndB());
        CharTextComparator cmp = new CharTextComparator();

        List<Edit> wordEdits = MyersDiff.INSTANCE.diff(cmp, a, b);

        // Combine edits that are really close together. If they are
        // just a few characters apart we tend to get better results
        // by joining them together and taking the whole span.
        //
        for (int j = 0; j < wordEdits.size() - 1; ) {
          Edit c = wordEdits.get(j);
          Edit n = wordEdits.get(j + 1);

          if (n.getBeginA() - c.getEndA() <= 5 || n.getBeginB() - c.getEndB() <= 5) {
            int ab = c.getBeginA();
            int ae = n.getEndA();

            int bb = c.getBeginB();
            int be = n.getEndB();

            if (canCoalesce(a, c.getEndA(), n.getBeginA())
                && canCoalesce(b, c.getEndB(), n.getBeginB())) {
              wordEdits.set(j, new Edit(ab, ae, bb, be));
              wordEdits.remove(j + 1);
              continue;
            }
          }

          j++;
        }

        // Apply some simple rules to fix up some of the edits. Our
        // logic above, along with our per-character difference tends
        // to produce some crazy stuff.
        //
        for (int j = 0; j < wordEdits.size(); j++) {
          Edit c = wordEdits.get(j);
          int ab = c.getBeginA();
          int ae = c.getEndA();

          int bb = c.getBeginB();
          int be = c.getEndB();

          // Sometimes the diff generator produces an INSERT or DELETE
          // right up against a REPLACE, but we only find this after
          // we've also played some shifting games on the prior edit.
          // If that happened to us, coalesce them together so we can
          // correct this mess for the user. If we don't we wind up
          // with silly stuff like "es" -> "es = Addresses".
          //
          if (1 < j) {
            Edit p = wordEdits.get(j - 1);
            if (p.getEndA() == ab || p.getEndB() == bb) {
              if (p.getEndA() == ab && p.getBeginA() < p.getEndA()) {
                ab = p.getBeginA();
              }
              if (p.getEndB() == bb && p.getBeginB() < p.getEndB()) {
                bb = p.getBeginB();
              }
              wordEdits.remove(--j);
            }
          }

          // We sometimes collapsed an edit together in a strange way,
          // such that the edges of each text is identical. Fix by
          // by dropping out that incorrectly replaced region.
          //
          while (ab < ae && bb < be && cmp.equals(a, ab, b, bb)) {
            ab++;
            bb++;
          }
          while (ab < ae && bb < be && cmp.equals(a, ae - 1, b, be - 1)) {
            ae--;
            be--;
          }

          // The leading part of an edit and its trailing part in the same
          // text might be identical. Slide down that edit and use the tail
          // rather than the leading bit.
          //
          while (0 < ab
              && ab < ae
              && a.charAt(ab - 1) != '\n'
              && cmp.equals(a, ab - 1, a, ae - 1)) {
            ab--;
            ae--;
          }
          if (!a.isLineStart(ab) || !a.contains(ab, ae, '\n')) {
            while (ab < ae && ae < a.size() && cmp.equals(a, ab, a, ae)) {
              ab++;
              ae++;
              if (a.charAt(ae - 1) == '\n') {
                break;
              }
            }
          }

          while (0 < bb
              && bb < be
              && b.charAt(bb - 1) != '\n'
              && cmp.equals(b, bb - 1, b, be - 1)) {
            bb--;
            be--;
          }
          if (!b.isLineStart(bb) || !b.contains(bb, be, '\n')) {
            while (bb < be && be < b.size() && cmp.equals(b, bb, b, be)) {
              bb++;
              be++;
              if (b.charAt(be - 1) == '\n') {
                break;
              }
            }
          }

          // If most of a line was modified except the LF was common, make
          // the LF part of the modification region. This is easier to read.
          //
          if (ab < ae //
              && (ab == 0 || a.charAt(ab - 1) == '\n') //
              && ae < a.size()
              && a.charAt(ae - 1) != '\n'
              && a.charAt(ae) == '\n') {
            ae++;
          }
          if (bb < be //
              && (bb == 0 || b.charAt(bb - 1) == '\n') //
              && be < b.size()
              && b.charAt(be - 1) != '\n'
              && b.charAt(be) == '\n') {
            be++;
          }

          wordEdits.set(j, new Edit(ab, ae, bb, be));
        }

        edits.set(i, new ReplaceEdit(e, wordEdits));
      }
    }

    return new IntraLineDiff(edits);
  }

  private static void combineLineEdits(
      List<Edit> edits, ImmutableSet<Edit> editsDueToRebase, Text a, Text b) {
    for (int j = 0; j < edits.size() - 1; ) {
      Edit c = edits.get(j);
      Edit n = edits.get(j + 1);

      if (editsDueToRebase.contains(c) || editsDueToRebase.contains(n)) {
        // Don't combine any edits which were identified as being introduced by a rebase as we would
        // lose that information because of the combination.
        continue;
      }

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

  private static boolean canCoalesce(CharText a, int b, int e) {
    while (b < e) {
      if (a.charAt(b++) == '\n') {
        return false;
      }
    }
    return true;
  }
}
