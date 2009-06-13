// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.git;

import static com.google.gerrit.client.data.PatchScriptSettings.Whitespace.IGNORE_NONE;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetAncestor;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.patch.DiffCacheContent;
import com.google.gerrit.server.patch.DiffCacheKey;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Imports a {@link PatchSet} from a {@link Commit}. */
public class PatchSetImporter {
  private final SelfPopulatingCache diffCache;
  private final ReviewDb db;
  private final Project.NameKey projectKey;
  private final Repository repo;
  private final RevCommit src;
  private final PatchSet dst;
  private final boolean isNew;
  private Transaction txn;
  private org.spearce.jgit.patch.Patch gitpatch;

  private PatchSetInfo info;
  private boolean infoIsNew;

  private final Map<String, Patch> patchExisting = new HashMap<String, Patch>();
  private final List<Patch> patchInsert = new ArrayList<Patch>();
  private final List<Patch> patchUpdate = new ArrayList<Patch>();

  private final Map<Integer, PatchSetAncestor> ancestorExisting =
      new HashMap<Integer, PatchSetAncestor>();
  private final List<PatchSetAncestor> ancestorInsert =
      new ArrayList<PatchSetAncestor>();
  private final List<PatchSetAncestor> ancestorUpdate =
      new ArrayList<PatchSetAncestor>();

  public PatchSetImporter(final GerritServer gs, final ReviewDb dstDb,
      final Project.NameKey proj, final Repository srcRepo,
      final RevCommit srcCommit, final PatchSet dstPatchSet,
      final boolean isNewPatchSet) {
    diffCache = gs.getDiffCache();
    db = dstDb;
    projectKey = proj;
    repo = srcRepo;
    src = srcCommit;
    dst = dstPatchSet;
    isNew = isNewPatchSet;
  }

  public void setTransaction(final Transaction t) {
    txn = t;
  }

  public PatchSetInfo getPatchSetInfo() {
    return info;
  }

  public void run() throws IOException, OrmException {
    gitpatch = readGitPatch();

    dst.setRevision(toRevId(src));

    if (!isNew) {
      // If we aren't a new patch set then we need to load the existing
      // files so we can update or delete them if there are corrections.
      //
      info = db.patchSetInfo().get(dst.getId());
      for (final Patch p : db.patches().byPatchSet(dst.getId())) {
        patchExisting.put(p.getFileName(), p);
      }
      for (final PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(
          dst.getId())) {
        ancestorExisting.put(a.getPosition(), a);
      }
    }

    importInfo();
    for (final FileHeader fh : gitpatch.getFiles()) {
      importFile(fh);
    }

    final boolean auto = txn == null;
    if (auto) {
      txn = db.beginTransaction();
    }
    if (isNew) {
      db.patchSets().insert(Collections.singleton(dst), txn);
    }
    if (infoIsNew) {
      db.patchSetInfo().insert(Collections.singleton(info), txn);
    } else {
      db.patchSetInfo().update(Collections.singleton(info), txn);
    }
    db.patches().insert(patchInsert, txn);
    db.patchSetAncestors().insert(ancestorInsert, txn);
    if (!isNew) {
      db.patches().update(patchUpdate, txn);
      db.patches().delete(patchExisting.values(), txn);

      db.patchSetAncestors().update(ancestorUpdate, txn);
      db.patchSetAncestors().delete(ancestorExisting.values(), txn);
    }
    if (auto) {
      txn.commit();
      txn = null;
    }
  }

  private void importInfo() throws OrmException {
    if (info == null) {
      info = new PatchSetInfo(dst.getId());
      infoIsNew = true;
    }

    info.setSubject(src.getShortMessage());
    info.setMessage(src.getFullMessage());
    info.setAuthor(toUserIdentity(src.getAuthorIdent()));
    info.setCommitter(toUserIdentity(src.getCommitterIdent()));

    for (int p = 0; p < src.getParentCount(); p++) {
      PatchSetAncestor a = ancestorExisting.remove(p + 1);
      if (a == null) {
        a = new PatchSetAncestor(new PatchSetAncestor.Id(dst.getId(), p + 1));
        ancestorInsert.add(a);
      } else {
        ancestorUpdate.add(a);
      }
      a.setAncestorRevision(toRevId(src.getParent(p)));
    }
  }

  private UserIdentity toUserIdentity(final PersonIdent who)
      throws OrmException {
    final UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    if (u.getEmail() != null) {
      // If only one account has access to this email address, select it
      // as the identity of the user.
      //
      final Set<Account.Id> a = new HashSet<Account.Id>();
      for (final AccountExternalId e : db.accountExternalIds().byEmailAddress(
          u.getEmail())) {
        a.add(e.getAccountId());
      }
      if (a.size() == 1) {
        u.setAccount(a.iterator().next());
      }
    }

    return u;
  }

  private void importFile(final FileHeader fh) {
    final String path;
    if (fh.getChangeType() == FileHeader.ChangeType.DELETE) {
      path = fh.getOldName();
    } else {
      path = fh.getNewName();
    }

    Patch p = patchExisting.remove(path);
    if (p == null) {
      p = new Patch(new Patch.Key(dst.getId(), path));
      patchInsert.add(p);
    } else {
      p.setSourceFileName(null);
      patchUpdate.add(p);
    }

    // Convert the ChangeType
    //
    if (fh.getChangeType() == FileHeader.ChangeType.ADD) {
      p.setChangeType(Patch.ChangeType.ADDED);

    } else if (fh.getChangeType() == FileHeader.ChangeType.MODIFY) {
      p.setChangeType(Patch.ChangeType.MODIFIED);

    } else if (fh.getChangeType() == FileHeader.ChangeType.DELETE) {
      p.setChangeType(Patch.ChangeType.DELETED);

    } else if (fh.getChangeType() == FileHeader.ChangeType.RENAME) {
      p.setChangeType(Patch.ChangeType.RENAMED);
      p.setSourceFileName(fh.getOldName());

    } else if (fh.getChangeType() == FileHeader.ChangeType.COPY) {
      p.setChangeType(Patch.ChangeType.COPIED);
      p.setSourceFileName(fh.getOldName());
    }

    // Convert the PatchType
    //
    if (fh instanceof CombinedFileHeader) {
      p.setPatchType(Patch.PatchType.N_WAY);

    } else if (fh.getPatchType() == FileHeader.PatchType.GIT_BINARY) {
      p.setPatchType(Patch.PatchType.BINARY);

    } else if (fh.getPatchType() == FileHeader.PatchType.BINARY) {
      p.setPatchType(Patch.PatchType.BINARY);
    }

    if (p.getPatchType() != Patch.PatchType.BINARY) {
      final byte[] buf = fh.getBuffer();
      for (int ptr = fh.getStartOffset(); ptr < fh.getEndOffset(); ptr++) {
        if (buf[ptr] == '\0') {
          // Its really binary, but Git couldn't see the nul early enough
          // to realize its binary, and instead produced the diff.
          //
          // Force it to be a binary; it really should have been that.
          //
          p.setPatchType(Patch.PatchType.BINARY);
          break;
        }
      }
    }
  }

  private static RevId toRevId(final RevCommit src) {
    return new RevId(src.getId().name());
  }

  private org.spearce.jgit.patch.Patch readGitPatch() throws IOException {
    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");
    args.add("-M");
    args.add("--full-index");

    final ObjectId a, b;
    switch (src.getParentCount()) {
      case 0:
        args.add("--unified=1");
        a = emptyTree();
        b = src.getId();
        break;
      case 1:
        args.add("--unified=1");
        a = src.getParent(0).getId();
        b = src.getId();
        break;
      default:
        args.add("--cc");
        a = null;
        b = src.getId();
        break;
    }
    if (a != null) {
      args.add(a.name());
    }
    args.add(b.name());

    final Process proc =
        Runtime.getRuntime().exec(args.toArray(new String[args.size()]), null,
            repo.getDirectory());
    final org.spearce.jgit.patch.Patch p;
    try {
      p = new org.spearce.jgit.patch.Patch();
      proc.getOutputStream().close();
      proc.getErrorStream().close();
      p.parse(proc.getInputStream());
      proc.getInputStream().close();
    } finally {
      try {
        if (proc.waitFor() != 0) {
          throw new IOException("git diff-tree exited abnormally");
        }
      } catch (InterruptedException ie) {
      }
    }

    for (final FileHeader fh : p.getFiles()) {
      final DiffCacheKey k;

      String srcName = null;
      switch (fh.getChangeType()) {
        case RENAME:
        case COPY:
          srcName = fh.getOldName();
          break;
      }

      final String newName = fh.getNewName();
      k = new DiffCacheKey(projectKey, a, b, newName, srcName, IGNORE_NONE);
      diffCache.put(new Element(k, DiffCacheContent.create(repo, k, fh)));
    }

    return p;
  }

  private ObjectId emptyTree() throws IOException {
    return new ObjectWriter(repo).writeCanonicalTree(new byte[0]);
  }
}
