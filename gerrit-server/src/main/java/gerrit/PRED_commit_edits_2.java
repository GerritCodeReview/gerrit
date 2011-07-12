// Copyright (C) 2011 The Android Open Source Project
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

package gerrit;

import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.patch.Text;

import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.Term;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Returns true if any of the files that match FileNameRegex have edited lines
 * that match EditRegex
 *
 * <pre>
 *   'commit_edits'(+FileNameRegex, +EditRegex)
 * </pre>
 */
public class PRED_commit_edits_2 extends Predicate.P2 {
  private static final long serialVersionUID = 1L;

  public PRED_commit_edits_2(Term a1, Term a2, Operation n) {
    arg1 = a1;
    arg2 = a2;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();

    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();
    if (a1.isVariable()) {
      throw new PInstantiationException(this, 1);
    }
    if (!a1.isSymbol()) {
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }
    Pattern regex = Pattern.compile(a1.name());

    if (a2.isVariable()) {
      throw new PInstantiationException(this, 1);
    }
    if (!a2.isSymbol()) {
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }
    Pattern regexEdit = Pattern.compile(a2.name());

    PrologEnvironment env = (PrologEnvironment) engine.control;
    PatchSetInfo psInfo;
    try {
      psInfo = getPatchSetInfo(env);
    } catch (PatchSetInfoNotAvailableException err) {
      throw new JavaException(this, 1, err);
    }

    PatchList pl = getPatchList(env, psInfo);
    Repository repo;
    try {
      repo = getRepository(env);
    } catch (RepositoryNotFoundException err) {
      throw new JavaException(this, 1, err);
    }

    final ObjectReader reader = repo.newObjectReader();
    final RevTree aTree;
    final RevTree bTree;
    try {
      final RevWalk rw = new RevWalk(reader);
      final RevCommit bCommit = rw.parseCommit(pl.getNewId());

      if (pl.getOldId() != null) {
        aTree = rw.parseTree(pl.getOldId());
      } else {
        final RevCommit p = bCommit.getParent(0);
        rw.parseHeaders(p);
        aTree = p.getTree();
      }
      bTree = bCommit.getTree();
      } catch (IOException err) {
        throw new JavaException(this, 1, err);
      } finally {
        reader.release();
    }

    for (PatchListEntry entry : pl.getPatches()) {
      String newName = entry.getNewName();
      String oldName = entry.getOldName();

      if (regex.matcher(newName).find() ||
          (oldName != null && regex.matcher(oldName).find())) {
        List<Edit> edits = entry.getEdits();

        if (edits.isEmpty()) {
          continue;
        }
        try {
          Text tA;
          if (oldName != null) {
            tA = load(aTree, oldName, repo);
          } else {
            tA = load(aTree, newName, repo);
          }
          Text tB = load(bTree, newName, repo);
          for (Edit edit : edits) {
            if (tA != Text.EMPTY) {
              String aDiff = tA.getString(edit.getBeginA(), edit.getEndA(), true);
              if (regexEdit.matcher(aDiff).find()) {
                engine.neckCut();
                return cont;
              }
            }
            if (tB != Text.EMPTY) {
              String bDiff = tB.getString(edit.getBeginB(), edit.getEndB(), true);
              if (regexEdit.matcher(bDiff).find()) {
                engine.neckCut();
                return cont;
              }
            }
          }
        } catch (IOException err) {
          throw new JavaException(this, 1, err);
        }
      }
    }

    return engine.fail();
  }

  protected PatchSetInfo getPatchSetInfo(PrologEnvironment env)
      throws PatchSetInfoNotAvailableException {
    PatchSetInfo psInfo = env.get(StoredValues.PATCH_SET_INFO);
    if (psInfo == null) {
      PatchSet.Id patchSetId = env.get(StoredValues.PATCH_SET_ID);
      PatchSetInfoFactory patchInfoFactory =
        env.getInjector().getInstance(PatchSetInfoFactory.class);
      psInfo = patchInfoFactory.get(patchSetId);
      env.set(StoredValues.PATCH_SET_INFO, psInfo);
    }

    return psInfo;
  }

  protected PatchList getPatchList(PrologEnvironment env, PatchSetInfo psInfo) {
    PatchList patchList = env.get(StoredValues.PATCH_LIST);
    if (patchList == null) {
      PatchListCache plCache = env.getInjector().getInstance(PatchListCache.class);
      Change change = env.get(StoredValues.CHANGE);
      Project.NameKey projectKey = change.getProject();
      ObjectId a = null;
      ObjectId b = ObjectId.fromString(psInfo.getRevId());
      Whitespace ws = Whitespace.IGNORE_NONE;
      PatchListKey plKey = new PatchListKey(projectKey, a, b, ws);
      patchList = plCache.get(plKey);
      env.set(StoredValues.PATCH_LIST, patchList);
    }

    return patchList;
  }

  protected Repository getRepository(PrologEnvironment env) throws RepositoryNotFoundException {
    Repository repo = env.get(StoredValues.REPOSITORY);
    if (repo == null) {
      GitRepositoryManager gitMgr =
        env.getInjector().getInstance(GitRepositoryManager.class);
      Change change = env.get(StoredValues.CHANGE);
      Project.NameKey projectKey = change.getProject();
      repo = gitMgr.openRepository(projectKey);
      env.set(StoredValues.REPOSITORY, repo);
    }

    return repo;
  }

  private Text load(final ObjectId tree, final String path, final Repository repo)
      throws MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException, IOException {
    if (path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(repo, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
      return Text.EMPTY;
    }
    return new Text(repo.open(tw.getObjectId(0), Constants.OBJ_BLOB));
  }
}