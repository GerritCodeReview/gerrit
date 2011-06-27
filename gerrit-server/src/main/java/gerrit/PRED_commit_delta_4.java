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
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;

import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.JavaException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.eclipse.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a regular expression, checks it against the file list in the most
 * recent patchset of a change. For all files that match the regex, returns the
 * (new) path of the file, the change type, and the old path of the file if
 * applicable (if the file was copied or renamed).
 *
 * <pre>
 *   'commit_delta'(+Regex, -NewPath, -ChangeType, -OldPath)
 * </pre>
 */
class PRED_commit_delta_4 extends Predicate.P4 {
  private static final long serialVersionUID = 1L;
  static final Operation commit_delta_4_top = new PRED_commit_delta_4_top();
  static final Operation commit_delta_check = new PRED_commit_delta_check();
  static final Operation commit_delta_next = new PRED_commit_delta_next();
  static final Operation commit_delta_modify = new PRED_commit_delta_modify();

  PRED_commit_delta_4(Term a1, Term a2, Term a3, Term a4, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    arg4 = a4;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.areg2 = arg2;
    engine.areg3 = arg3;
    engine.areg4 = arg4;
    engine.cont = cont;
    engine.setB0();
    Term a1 = arg1.dereference();

    if (a1.isVariable()) {
      throw new PInstantiationException(this, 1);
    }
    if (!a1.isSymbol()) {
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }

    PrologEnvironment env = (PrologEnvironment) engine.control;
    PatchSetInfo psInfo;
    try {
      psInfo = getPatchSetInfo(env);
    } catch (PatchSetInfoNotAvailableException err) {
      throw new JavaException(this, 1, err);
    }
    UserIdentity author = psInfo.getAuthor();

    PatchListCache plCache = env.getInjector().getInstance(PatchListCache.class);
    Change change = StoredValues.CHANGE.get(engine);

    Project.NameKey projectKey = change.getProject();
    ObjectId a = null;
    ObjectId b = ObjectId.fromString(psInfo.getRevId());
    Whitespace ws = Whitespace.IGNORE_NONE;
    PatchListKey plKey = new PatchListKey(projectKey, a, b, ws);

    //make a modifiable copy of the patches list
    List<PatchListEntry> patches = new ArrayList<PatchListEntry>();
    PatchList pl = plCache.get(plKey);
    for (PatchListEntry entry : pl.getPatches()) {
      patches.add(entry);
    }

    engine.areg1 = arg1;
    engine.areg5 = new JavaObjectTerm(patches);

    return commit_delta_4_top;
  }


  private static final class PRED_commit_delta_4_top extends Operation {
    @Override
    public Operation exec(Prolog engine) {
        engine.setB0();
        return engine.jtry5(commit_delta_check, commit_delta_next);
    }
  }

  private static final class PRED_commit_delta_check extends Operation {
    @Override
    public Operation exec(Prolog engine) {
      Term a1 = engine.areg1;
      Term a2 = engine.areg2;
      Term a3 = engine.areg3;
      Term a4 = engine.areg4;
      Term a5 = engine.areg5;
      a1.dereference();
      a2.dereference();
      a3.dereference();
      a4.dereference();
      a5.dereference();

      String regex = a1.toString();
      List<PatchListEntry> patches =
        (List<PatchListEntry>)((JavaObjectTerm)a5).object();
      if (!patches.isEmpty()) {
        PatchListEntry patch = patches.get(0);
        String newName = patch.getNewName();
        String oldName = patch.getOldName();
        Patch.ChangeType changeType = patch.getChangeType();

        if ((oldName != null && oldName.matches(regex))
            || newName.matches(regex)) {
          SymbolTerm newSym = SymbolTerm.create(newName);
          SymbolTerm changeSym = SymbolTerm.intern(changeType.toString());
          SymbolTerm oldSym = Prolog.Nil;
          if (oldName != null) {
            oldSym = SymbolTerm.create(oldName);
          }

          if (!a2.unify(newSym, engine.trail)) {
            return engine.fail();
          }
          if (!a3.unify(changeSym, engine.trail)) {
            return engine.fail();
          }
          if (!a4.unify(oldSym, engine.trail)) {
            return engine.fail();
          }
          return engine.cont;
        }
      }
      return engine.fail();
    }
  }

  private static final class PRED_commit_delta_next extends Operation {
    @Override
    public Operation exec(Prolog engine) {
        return engine.trust(commit_delta_modify);
    }
  }

  private static final class PRED_commit_delta_modify extends Operation {
    @Override
    public Operation exec(Prolog engine) {
        Term a5 = engine.areg5;
        a5.dereference();

        List<PatchListEntry> patches =
          (List<PatchListEntry>)((JavaObjectTerm)a5).object();
        if (patches.isEmpty()) {
          return engine.fail();
        }
        patches.remove(0);

        engine.areg5 = new JavaObjectTerm(patches);

        return commit_delta_4_top;
    }
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
}