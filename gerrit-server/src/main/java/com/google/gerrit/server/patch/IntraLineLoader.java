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

import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

class IntraLineLoader extends EntryCreator<IntraLineDiffKey, IntraLineDiff> {
  private static final Logger log = LoggerFactory
      .getLogger(IntraLineLoader.class);

  private static final Pattern BLANK_LINE_RE = Pattern
      .compile("^[ \\t]*(|[{}]|/\\*\\*?|\\*)[ \\t]*$");

  private static final Pattern CONTROL_BLOCK_START_RE = Pattern
      .compile("[{:][ \\t]*$");

  private final BlockingQueue<Worker> workerPool;
  private final long timeoutMillis;

  @Inject
  IntraLineLoader(final @GerritServerConfig Config cfg) {
    final int workers =
        cfg.getInt("cache", PatchListCacheImpl.INTRA_NAME, "maxIdleWorkers",
            Runtime.getRuntime().availableProcessors() * 3 / 2);
    workerPool = new ArrayBlockingQueue<Worker>(workers, true /* fair */);

    timeoutMillis =
        ConfigUtil.getTimeUnit(cfg, "cache", PatchListCacheImpl.INTRA_NAME,
            "timeout", TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
  }

  @Override
  public IntraLineDiff createEntry(IntraLineDiffKey key) throws Exception {
    Worker w = workerPool.poll();
    if (w == null) {
      w = new Worker();
    }

    Worker.Result r = w.computeWithTimeout(key, timeoutMillis);

    if (r == Worker.Result.TIMEOUT) {
      // Don't keep this thread. We have to murder it unsafely, which
      // means its unable to be reused in the future. Return back a
      // null result, indicating the cache cannot load this key.
      //
      return new IntraLineDiff(IntraLineDiff.Status.TIMEOUT);
    }

    if (!workerPool.offer(w)) {
      // If the idle worker pool is full, terminate this thread.
      //
      w.end();
    }

    if (r.error != null) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      //
      throw r.error;
    }

    return r.diff;
  }

  private static class Worker {
    private static final AtomicInteger count = new AtomicInteger(1);

    private final ArrayBlockingQueue<Input> input;
    private final ArrayBlockingQueue<Result> result;
    private final Thread thread;

    Worker() {
      input = new ArrayBlockingQueue<Input>(1);
      result = new ArrayBlockingQueue<Result>(1);

      thread = new Thread(new Runnable() {
        public void run() {
          workerLoop();
        }
      });
      thread.setName("IntraLineDiff-" + count.getAndIncrement());
      thread.setDaemon(true);
      thread.start();
    }

    Result computeWithTimeout(IntraLineDiffKey key, long timeoutMillis)
        throws Exception {
      if (!input.offer(new Input(key))) {
        log.error("Cannot enqueue task to thread " + thread.getName());
        return null;
      }

      Result r = result.poll(timeoutMillis, TimeUnit.MILLISECONDS);
      if (r != null) {
        return r;
      } else {
        log.warn(timeoutMillis + " ms timeout reached for IntraLineDiff"
            + " in project " + key.getProject().get() //
            + " on commit " + key.getCommit().name() //
            + " for path " + key.getPath() //
            + " comparing " + key.getBlobA().name() //
            + ".." + key.getBlobB().name() //
            + ".  Killing " + thread.getName());
        forcefullyKillThreadInAnUglyWay();
        return Result.TIMEOUT;
      }
    }

    @SuppressWarnings("deprecation")
    private void forcefullyKillThreadInAnUglyWay() {
      try {
        thread.stop();
      } catch (Throwable error) {
        // Ignore any reason the thread won't stop.
        log.error("Cannot stop runaway thread " + thread.getName(), error);
      }
    }

    void end() {
      if (!input.offer(Input.END_THREAD)) {
        log.error("Cannot gracefully stop thread " + thread.getName());
      }
    }

    private void workerLoop() {
      try {
        for (;;) {
          Input in;
          try {
            in = input.take();
          } catch (InterruptedException e) {
            log.error("Unexpected interrupt on " + thread.getName());
            continue;
          }

          if (in == Input.END_THREAD) {
            return;
          }

          Result r;
          try {
            r = new Result(IntraLineLoader.compute(in.key));
          } catch (Exception error) {
            r = new Result(error);
          }

          if (!result.offer(r)) {
            log.error("Cannot return result from " + thread.getName());
          }
        }
      } catch (ThreadDeath iHaveBeenShot) {
        // Handle thread death by gracefully returning to the caller,
        // allowing the thread to be destroyed.
      }
    }

    private static class Input {
      static final Input END_THREAD = new Input(null);

      final IntraLineDiffKey key;

      Input(IntraLineDiffKey key) {
        this.key = key;
      }
    }

    static class Result {
      static final Result TIMEOUT = new Result((IntraLineDiff) null);

      final IntraLineDiff diff;
      final Exception error;

      Result(IntraLineDiff diff) {
        this.diff = diff;
        this.error = null;
      }

      Result(Exception error) {
        this.diff = null;
        this.error = error;
      }
    }
  }

  private static IntraLineDiff compute(IntraLineDiffKey key) throws Exception {
    List<Edit> edits = new ArrayList<Edit>(key.getEdits());
    Text aContent = key.getTextA();
    Text bContent = key.getTextB();
    combineLineEdits(edits, aContent, bContent);

    for (int i = 0; i < edits.size(); i++) {
      Edit e = edits.get(i);

      if (e.getType() == Edit.Type.REPLACE) {
        CharText a = new CharText(aContent, e.getBeginA(), e.getEndA());
        CharText b = new CharText(bContent, e.getBeginB(), e.getEndB());
        CharTextComparator cmp = new CharTextComparator();

        List<Edit> wordEdits = MyersDiff.INSTANCE.diff(cmp, a, b);

        // Combine edits that are really close together. If they are
        // just a few characters apart we tend to get better results
        // by joining them together and taking the whole span.
        //
        for (int j = 0; j < wordEdits.size() - 1;) {
          Edit c = wordEdits.get(j);
          Edit n = wordEdits.get(j + 1);

          if (n.getBeginA() - c.getEndA() <= 5
              || n.getBeginB() - c.getEndB() <= 5) {
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
          // rather than the leading bit. If however the edit is only on a
          // whitespace block try to shift it to the left margin, assuming
          // that it is an indentation change.
          //
          boolean aShift = true;
          if (ab < ae && isOnlyWhitespace(a, ab, ae)) {
            int lf = findLF(wordEdits, j, a, ab);
            if (lf < ab && a.charAt(lf) == '\n') {
              int nb = lf + 1;
              int p = 0;
              while (p < ae - ab) {
                if (cmp.equals(a, ab + p, a, ab + p))
                  p++;
                else
                  break;
              }
              if (p == ae - ab) {
                ab = nb;
                ae = nb + p;
                aShift = false;
              }
            }
          }
          if (aShift) {
            while (0 < ab && ab < ae && a.charAt(ab - 1) != '\n'
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
          }

          boolean bShift = true;
          if (bb < be && isOnlyWhitespace(b, bb, be)) {
            int lf = findLF(wordEdits, j, b, bb);
            if (lf < bb && b.charAt(lf) == '\n') {
              int nb = lf + 1;
              int p = 0;
              while (p < be - bb) {
                if (cmp.equals(b, bb + p, b, bb + p))
                  p++;
                else
                  break;
              }
              if (p == be - bb) {
                bb = nb;
                be = nb + p;
                bShift = false;
              }
            }
          }
          if (bShift) {
            while (0 < bb && bb < be && b.charAt(bb - 1) != '\n'
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
          }

          // If most of a line was modified except the LF was common, make
          // the LF part of the modification region. This is easier to read.
          //
          if (ab < ae //
              && (ab == 0 || a.charAt(ab - 1) == '\n') //
              && ae < a.size() && a.charAt(ae) == '\n') {
            ae++;
          }
          if (bb < be //
              && (bb == 0 || b.charAt(bb - 1) == '\n') //
              && be < b.size() && b.charAt(be) == '\n') {
            be++;
          }

          wordEdits.set(j, new Edit(ab, ae, bb, be));
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

  private static boolean canCoalesce(CharText a, int b, int e) {
    while (b < e) {
      if (a.charAt(b++) == '\n') {
        return false;
      }
    }
    return true;
  }

  private static int findLF(List<Edit> edits, int j, CharText t, int b) {
    int lf = b;
    int limit = 0 < j ? edits.get(j - 1).getEndB() : 0;
    while (limit < lf && t.charAt(lf) != '\n') {
      lf--;
    }
    return lf;
  }

  private static boolean isOnlyWhitespace(CharText t, final int b, final int e) {
    for (int c = b; c < e; c++) {
      if (!Character.isWhitespace(t.charAt(c))) {
        return false;
      }
    }
    return b < e;
  }
}
