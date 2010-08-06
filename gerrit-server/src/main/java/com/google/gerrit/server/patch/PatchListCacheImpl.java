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
// Some portions (e.g. outputDiff) below are:
//
// Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
// Copyright (C) 2009, Johannes E. Schindelin
// Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
// and other copyright owners as documented in the project's IP log.
//
// This program and the accompanying materials are made available
// under the terms of the Eclipse Distribution License v1.0 which
// accompanies this distribution, is reproduced below, and is
// available at http://www.eclipse.org/org/documents/edl-v10.php
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the following
// conditions are met:
//
// - Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//
// - Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
//
// - Neither the name of the Eclipse Foundation, Inc. nor the
// names of its contributors may be used to endorse or promote
// products derived from this software without specific prior
// written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
// NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

package com.google.gerrit.server.patch;


import com.google.gerrit.prettify.common.BaseEdit;
import com.google.gerrit.prettify.common.LineEdit;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.cache.EvictionPolicy;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextIgnoreAllWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreTrailingWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreWhitespaceChange;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Provides a cached list of {@link PatchListEntry}. */
@Singleton
public class PatchListCacheImpl implements PatchListCache {
  private static final String CACHE_NAME = "diff";

  private static final Pattern BLANK_LINE_RE =
      Pattern.compile("^[ \\t]*(|[{}]|/\\*\\*?|\\*)[ \\t]*$");
  private static final Pattern CONTROL_BLOCK_START_RE =
      Pattern.compile("[{:][ \\t]*$");

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<PatchListKey, PatchList>> type =
            new TypeLiteral<Cache<PatchListKey, PatchList>>() {};
        disk(type, CACHE_NAME) //
            .memoryLimit(128) // very large items, cache only a few
            .evictionPolicy(EvictionPolicy.LRU) // prefer most recent
            .populateWith(Loader.class) //
        ;
        bind(PatchListCacheImpl.class);
        bind(PatchListCache.class).to(PatchListCacheImpl.class);
      }
    };
  }

  private final Cache<PatchListKey, PatchList> cache;

  @Inject
  PatchListCacheImpl(
      @Named(CACHE_NAME) final Cache<PatchListKey, PatchList> thecache) {
    cache = thecache;
  }

  public PatchList get(final PatchListKey key) {
    return cache.get(key);
  }

  public PatchList get(final Change change, final PatchSet patchSet) {
    return get(change, patchSet, Whitespace.IGNORE_NONE);
  }

  public PatchList get(final Change change, final PatchSet patchSet,
      final Whitespace whitespace) {
    final Project.NameKey projectKey = change.getProject();
    final ObjectId a = null;
    final ObjectId b = ObjectId.fromString(patchSet.getRevision().get());
    return get(new PatchListKey(projectKey, a, b, whitespace));
  }

  static class Loader extends EntryCreator<PatchListKey, PatchList> {
    private final GitRepositoryManager repoManager;
    private final boolean computeIntraline;

    @Inject
    Loader(GitRepositoryManager mgr, @GerritServerConfig Config config) {
      repoManager = mgr;
      computeIntraline = config.getBoolean("cache", "diff", "intraline", true);
    }

    @Override
    public PatchList createEntry(final PatchListKey key) throws Exception {
      final Repository repo = repoManager.openRepository(key.projectKey.get());
      try {
        return readPatchList(key, repo);
      } finally {
        repo.close();
      }
    }

    private PatchList readPatchList(final PatchListKey key,
        final Repository repo) throws IOException {
      // TODO(jeffschu) correctly handle merge commits

      final RevWalk rw = new RevWalk(repo);
      final RevCommit b = rw.parseCommit(key.getNewId());
      final AnyObjectId a = aFor(key, repo, b);

      if (a == null) {
        return new PatchList(a, b, computeIntraline, new PatchListEntry[0]);
      }

      RevTree aTree = rw.parseTree(a);
      RevTree bTree = b.getTree();

      final TreeWalk walk = new TreeWalk(repo);
      walk.reset();
      walk.setRecursive(true);
      walk.addTree(aTree);
      walk.addTree(bTree);
      walk.setFilter(TreeFilter.ANY_DIFF);

      DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
      df.setRepository(repo);
      switch (key.getWhitespace()) {
        case IGNORE_ALL_SPACE:
          df.setRawTextFactory(RawTextIgnoreAllWhitespace.FACTORY);
          break;
        case IGNORE_NONE:
          df.setRawTextFactory(RawText.FACTORY);
          break;
        case IGNORE_SPACE_AT_EOL:
          df.setRawTextFactory(RawTextIgnoreTrailingWhitespace.FACTORY);
          break;
        case IGNORE_SPACE_CHANGE:
          df.setRawTextFactory(RawTextIgnoreWhitespaceChange.FACTORY);
          break;
      }

      RenameDetector rd = new RenameDetector(repo);
      rd.addAll(DiffEntry.scan(walk));
      List<DiffEntry> diffEntries = rd.compute();

      final int cnt = diffEntries.size();
      final PatchListEntry[] entries = new PatchListEntry[cnt];
      for (int i = 0; i < cnt; i++) {
        FileHeader fh = df.createFileHeader(diffEntries.get(i));
        entries[i] = newEntry(repo, aTree, bTree, fh);
      }
      return new PatchList(a, b, computeIntraline, entries);
    }

    private static List<LineEdit> editsToLineEdits(List<Edit> edits) {
      List<LineEdit> l = new ArrayList<LineEdit>(edits.size());
      for (Edit e : edits) {
        l.add(new LineEdit(e));
      }
      return l;
    }

    private static List<BaseEdit> editsToBaseEdits(List<Edit> edits) {
      List<BaseEdit> l = new ArrayList<BaseEdit>(edits.size());
      for (Edit e : edits) {
        l.add(new BaseEdit(e));
      }
      return l;
    }

    private PatchListEntry newEntry(Repository repo, RevTree aTree,
        RevTree bTree, FileHeader fileHeader) throws IOException {
      final FileMode oldMode = fileHeader.getOldMode();
      final FileMode newMode = fileHeader.getNewMode();

      if (oldMode == FileMode.GITLINK || newMode == FileMode.GITLINK) {
        return new PatchListEntry(fileHeader, Collections.<LineEdit> emptyList());
      }

      if (aTree == null // want combined diff
          || fileHeader.getPatchType() != PatchType.UNIFIED
          || fileHeader.getHunks().isEmpty()) {
        return new PatchListEntry(fileHeader, Collections.<LineEdit> emptyList());
      }

      List<LineEdit> edits = editsToLineEdits(fileHeader.toEditList());
      if (edits.isEmpty()) {
        return new PatchListEntry(fileHeader, Collections.<LineEdit> emptyList());
      }
      if (!computeIntraline) {
        return new PatchListEntry(fileHeader, edits);
      }

      switch (fileHeader.getChangeType()) {
        case ADD:
        case DELETE:
          return new PatchListEntry(fileHeader, edits);
      }

      Text aContent = null;
      Text bContent = null;

      for (int i = 0; i < edits.size(); i++) {
        LineEdit e = edits.get(i);

        if (e.getType() == Edit.Type.REPLACE) {
          if (aContent == null) {
            edits = new ArrayList<LineEdit>(edits);
            aContent = read(repo, fileHeader.getOldPath(), aTree);
            bContent = read(repo, fileHeader.getNewPath(), bTree);
            combineLineEdits(edits, aContent, bContent);
            i = -1; // restart the entire scan after combining lines.
            continue;
          }

          CharText a = new CharText(aContent, e.getBeginA(), e.getEndA());
          CharText b = new CharText(bContent, e.getBeginB(), e.getEndB());

          List<BaseEdit> wordEdits = editsToBaseEdits(new MyersDiff(a, b).getEdits());

          // Combine edits that are really close together. If they are
          // just a few characters apart we tend to get better results
          // by joining them together and taking the whole span.
          //
          for (int j = 0; j < wordEdits.size() - 1;) {
            BaseEdit c = wordEdits.get(j);
            BaseEdit n = wordEdits.get(j + 1);

            if (n.getBeginA() - c.getEndA() <= 5
                || n.getBeginB() - c.getEndB() <= 5) {
              int ab = c.getBeginA();
              int ae = n.getEndA();

              int bb = c.getBeginB();
              int be = n.getEndB();

              if (canCoalesce(a, c.getEndA(), n.getBeginA())
                  && canCoalesce(b, c.getEndB(), n.getBeginB())) {
                wordEdits.set(j, new LineEdit(ab, ae, bb, be));
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
            BaseEdit c = wordEdits.get(j);
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
              BaseEdit p = wordEdits.get(j - 1);
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
            while (ab < ae && bb < be && a.equals(ab, b, bb)) {
              ab++;
              bb++;
            }
            while (ab < ae && bb < be && a.equals(ae - 1, b, be - 1)) {
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
                  if (a.equals(ab + p, a, ab + p))
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
                  && a.equals(ab - 1, a, ae - 1)) {
                ab--;
                ae--;
              }
              if (!a.isLineStart(ab) || !a.contains(ab, ae, '\n')) {
                while (ab < ae && ae < a.size() && a.equals(ab, a, ae)) {
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
                  if (b.equals(bb + p, b, bb + p))
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
                  && b.equals(bb - 1, b, be - 1)) {
                bb--;
                be--;
              }
              if (!b.isLineStart(bb) || !b.contains(bb, be, '\n')) {
                while (bb < be && be < b.size() && b.equals(bb, b, be)) {
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

            wordEdits.set(j, new BaseEdit(ab, ae, bb, be));
          }

          edits.set(i, new LineEdit(e, wordEdits));
        }
      }

      return new PatchListEntry(fileHeader, edits);
    }

    private static void combineLineEdits(List<LineEdit> edits, Text a, Text b) {
      for (int j = 0; j < edits.size() - 1;) {
        BaseEdit c = edits.get(j);
        BaseEdit n = edits.get(j + 1);

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

          edits.set(j, new LineEdit(ab, ae, bb, be));
          edits.remove(j + 1);
          continue;
        }

        j++;
      }
    }

    private static boolean isBlankLineGap(Text a, int b, int e) {
      for (; b < e; b++) {
        if (!BLANK_LINE_RE.matcher(a.getLine(b)).matches()) {
          return false;
        }
      }
      return true;
    }

    private static boolean isControlBlockStart(Text a, int idx) {
      final String l = a.getLine(idx);
      return CONTROL_BLOCK_START_RE.matcher(l).find();
    }

    private static boolean canCoalesce(CharText a, int b, int e) {
      while (b < e) {
        if (a.charAt(b++) == '\n') {
          return false;
        }
      }
      return true;
    }

    private static int findLF(List<BaseEdit> edits, int j, CharText t, int b) {
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

    private static Text read(Repository repo, String path, RevTree tree)
        throws IOException {
      TreeWalk tw = TreeWalk.forPath(repo, path, tree);
      if (tw == null || tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
        return Text.EMPTY;
      }
      return new Text(repo.open(tw.getObjectId(0), Constants.OBJ_BLOB));
    }

    private static AnyObjectId aFor(final PatchListKey key,
        final Repository repo, final RevCommit b) throws IOException {
      if (key.getOldId() != null) {
        return key.getOldId();
      }

      switch (b.getParentCount()) {
        case 0:
          return emptyTree(repo);
        case 1:
          return b.getParent(0);
        default:
          // merge commit, return null to force combined diff behavior
          return null;
      }
    }

    private static ObjectId emptyTree(final Repository repo) throws IOException {
      ObjectInserter oi = repo.newObjectInserter();
      try {
        ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
        oi.flush();
        return id;
      } finally {
        oi.release();
      }
    }
  }
}
