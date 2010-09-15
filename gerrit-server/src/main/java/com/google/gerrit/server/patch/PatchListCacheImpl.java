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


import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
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
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextIgnoreAllWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreTrailingWhitespace;
import org.eclipse.jgit.diff.RawTextIgnoreWhitespaceChange;
import org.eclipse.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
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

      RawText.Factory rawTextFactory;
      switch (key.getWhitespace()) {
        case IGNORE_ALL_SPACE:
          rawTextFactory = RawTextIgnoreAllWhitespace.FACTORY;
          break;
        case IGNORE_SPACE_AT_EOL:
          rawTextFactory = RawTextIgnoreTrailingWhitespace.FACTORY;
          break;
        case IGNORE_SPACE_CHANGE:
          rawTextFactory = RawTextIgnoreWhitespaceChange.FACTORY;
          break;
        case IGNORE_NONE:
        default:
          rawTextFactory = RawText.FACTORY;
          break;
      }

      final ObjectReader reader = repo.newObjectReader();
      try {
        final RevWalk rw = new RevWalk(reader);
        final RevCommit b = rw.parseCommit(key.getNewId());
        final RevObject a = aFor(key, repo, rw, b);

        if (a == null) {
          // This is a merge commit, compared to its ancestor.
          //
          final PatchListEntry[] entries = new PatchListEntry[1];
          entries[0] = newCommitMessage(rawTextFactory, repo, reader, null, b);
          return new PatchList(a, b, computeIntraline, true, entries);
        }

        final boolean againstParent =
            b.getParentCount() > 0 && b.getParent(0) == a;

        RevCommit aCommit;
        RevTree aTree;
        if (a instanceof RevCommit) {
          aCommit = (RevCommit) a;
          aTree = aCommit.getTree();
        } else if (a instanceof RevTree) {
          aCommit = null;
          aTree = (RevTree) a;
        } else {
          throw new IOException("Unexpected type: " + a.getClass());
        }

        RevTree bTree = b.getTree();

        final TreeWalk walk = new TreeWalk(reader);
        walk.reset();
        walk.setRecursive(true);
        walk.addTree(aTree);
        walk.addTree(bTree);
        walk.setFilter(TreeFilter.ANY_DIFF);

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repo);
        df.setRawTextFactory(rawTextFactory);
        df.setDetectRenames(true);
        List<DiffEntry> diffEntries = df.scan(aTree, bTree);

        final int cnt = diffEntries.size();
        final PatchListEntry[] entries = new PatchListEntry[1 + cnt];
        entries[0] = newCommitMessage(rawTextFactory, repo, reader, //
            againstParent ? null : aCommit, b);
        for (int i = 0; i < cnt; i++) {
          FileHeader fh = df.toFileHeader(diffEntries.get(i));
          entries[1 + i] = newEntry(reader, aTree, bTree, fh);
        }
        return new PatchList(a, b, computeIntraline, againstParent, entries);
      } finally {
        reader.release();
      }
    }

    private PatchListEntry newCommitMessage(
        final RawText.Factory rawTextFactory, final Repository db,
        final ObjectReader reader, final RevCommit aCommit,
        final RevCommit bCommit) throws IOException {
      StringBuilder hdr = new StringBuilder();

      hdr.append("diff --git");
      if (aCommit != null) {
        hdr.append(" a/" + Patch.COMMIT_MSG);
      } else {
        hdr.append(" " + FileHeader.DEV_NULL);
      }
      hdr.append(" b/" + Patch.COMMIT_MSG);
      hdr.append("\n");

      if (aCommit != null) {
        hdr.append("--- a/" + Patch.COMMIT_MSG + "\n");
      } else {
        hdr.append("--- " + FileHeader.DEV_NULL + "\n");
      }
      hdr.append("+++ b/" + Patch.COMMIT_MSG + "\n");

      Text aText = aCommit != null ? Text.forCommit(db, reader, aCommit) : Text.EMPTY;
      Text bText = Text.forCommit(db, reader, bCommit);

      byte[] rawHdr = hdr.toString().getBytes("UTF-8");
      RawText aRawText = rawTextFactory.create(aText.getContent());
      RawText bRawText = rawTextFactory.create(bText.getContent());
      EditList edits = new MyersDiff(aRawText, bRawText).getEdits();
      FileHeader fh = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
      return newEntry(reader, aText, bText, edits, null, null, fh);
    }

    private PatchListEntry newEntry(ObjectReader reader, RevTree aTree,
        RevTree bTree, FileHeader fileHeader) throws IOException {
      final FileMode oldMode = fileHeader.getOldMode();
      final FileMode newMode = fileHeader.getNewMode();

      if (oldMode == FileMode.GITLINK || newMode == FileMode.GITLINK) {
        return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
      }

      if (aTree == null // want combined diff
          || fileHeader.getPatchType() != PatchType.UNIFIED
          || fileHeader.getHunks().isEmpty()) {
        return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
      }

      List<Edit> edits = fileHeader.toEditList();
      if (edits.isEmpty()) {
        return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
      }
      if (!computeIntraline) {
        return new PatchListEntry(fileHeader, edits);
      }

      switch (fileHeader.getChangeType()) {
        case ADD:
        case DELETE:
          return new PatchListEntry(fileHeader, edits);
      }

      return newEntry(reader, null, null, edits, aTree, bTree, fileHeader);
    }

    private PatchListEntry newEntry(ObjectReader reader, Text aContent,
        Text bContent, List<Edit> edits, RevTree aTree, RevTree bTree,
        FileHeader fileHeader) throws IOException {
      for (int i = 0; i < edits.size(); i++) {
        Edit e = edits.get(i);

        if (e.getType() == Edit.Type.REPLACE) {
          if (aContent == null) {
            edits = new ArrayList<Edit>(edits);
            aContent = read(reader, fileHeader.getOldPath(), aTree);
            bContent = read(reader, fileHeader.getNewPath(), bTree);
            combineLineEdits(edits, aContent, bContent);
            i = -1; // restart the entire scan after combining lines.
            continue;
          }

          CharText a = new CharText(aContent, e.getBeginA(), e.getEndA());
          CharText b = new CharText(bContent, e.getBeginB(), e.getEndB());

          List<Edit> wordEdits = new MyersDiff(a, b).getEdits();

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

            wordEdits.set(j, new Edit(ab, ae, bb, be));
          }

          edits.set(i, new ReplaceEdit(e, wordEdits));
        }
      }

      return new PatchListEntry(fileHeader, edits);
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

    private static Text read(ObjectReader reader, String path, RevTree tree)
        throws IOException {
      TreeWalk tw = TreeWalk.forPath(reader, path, tree);
      if (tw == null || tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
        return Text.EMPTY;
      }
      ObjectLoader ldr;
      try {
        ldr = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);
      } catch (MissingObjectException notFound) {
        return Text.EMPTY;
      }
      return new Text(ldr);
    }

    private static RevObject aFor(final PatchListKey key,
        final Repository repo, final RevWalk rw, final RevCommit b)
        throws IOException {
      if (key.getOldId() != null) {
        return rw.parseAny(key.getOldId());
      }

      switch (b.getParentCount()) {
        case 0:
          return rw.parseAny(emptyTree(repo));
        case 1:{
          RevCommit r = b.getParent(0);
          rw.parseBody(r);
          return r;
        }
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
