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

import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetAncestor;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Imports a {@link PatchSet} from a {@link Commit}. */
public class PatchSetImporter {
  public interface Factory {
    PatchSetImporter create(ReviewDb dstDb, RevCommit srcCommit,
        PatchSet dstPatchSet, boolean isNewPatchSet);
  }

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final RevCommit src;
  private final PatchSet dst;
  private final boolean isNew;
  private Transaction txn;

  private PatchSetInfo info;

  private final Map<Integer, PatchSetAncestor> ancestorExisting =
      new HashMap<Integer, PatchSetAncestor>();
  private final List<PatchSetAncestor> ancestorInsert =
      new ArrayList<PatchSetAncestor>();
  private final List<PatchSetAncestor> ancestorUpdate =
      new ArrayList<PatchSetAncestor>();

  @Inject
  PatchSetImporter(final PatchSetInfoFactory psif,
      @Assisted final ReviewDb dstDb, @Assisted final RevCommit srcCommit,
      @Assisted final PatchSet dstPatchSet,
      @Assisted final boolean isNewPatchSet) {
    patchSetInfoFactory = psif;
    db = dstDb;
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

  public void run() throws OrmException {
    dst.setRevision(toRevId(src));

    if (!isNew) {
      for (final PatchSetAncestor a : db.patchSetAncestors().ancestorsOf(
          dst.getId())) {
        ancestorExisting.put(a.getPosition(), a);
      }
    }

    info = patchSetInfoFactory.get(src, dst.getId());
    importAncestors();

    final boolean auto = txn == null;
    if (auto) {
      txn = db.beginTransaction();
    }
    if (isNew) {
      db.patchSets().insert(Collections.singleton(dst), txn);
    }
    db.patchSetAncestors().insert(ancestorInsert, txn);
    if (!isNew) {
      db.patchSetAncestors().update(ancestorUpdate, txn);
      db.patchSetAncestors().delete(ancestorExisting.values(), txn);
    }
    if (auto) {
      txn.commit();
      txn = null;
    }
  }

  private void importAncestors() {
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

  private static RevId toRevId(final RevCommit src) {
    return new RevId(src.getId().name());
  }
}
