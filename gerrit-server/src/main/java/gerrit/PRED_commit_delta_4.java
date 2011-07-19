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

import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;

import com.googlecode.prolog_cafe.lang.IllegalTypeException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.PInstantiationException;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologException;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Given a regular expression, checks it against the file list in the most
 * recent patchset of a change. For all files that match the regex, returns the
 * (new) path of the file, the change type, and the old path of the file if
 * applicable (if the file was copied or renamed).
 *
 * <pre>
 *   'commit_delta'(+Regex, -ChangeType, -NewPath, -OldPath)
 * </pre>
 */
public class PRED_commit_delta_4 extends Predicate.P4 {
  private static final long serialVersionUID = 1L;
  private static final SymbolTerm add = SymbolTerm.intern("add");
  private static final SymbolTerm modify = SymbolTerm.intern("modify");
  private static final SymbolTerm delete = SymbolTerm.intern("delete");
  private static final SymbolTerm rename = SymbolTerm.intern("rename");
  private static final SymbolTerm copy = SymbolTerm.intern("copy");
  static final Operation commit_delta_check = new PRED_commit_delta_check();
  static final Operation commit_delta_next = new PRED_commit_delta_next();
  static final Operation commit_delta_empty = new PRED_commit_delta_empty();

  public PRED_commit_delta_4(Term a1, Term a2, Term a3, Term a4, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    arg4 = a4;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.cont = cont;
    engine.setB0();

    Term a1 = arg1.dereference();
    if (a1.isVariable()) {
      throw new PInstantiationException(this, 1);
    }
    if (!a1.isSymbol()) {
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }
    Pattern regex = Pattern.compile(a1.name());
    engine.areg1 = new JavaObjectTerm(regex);
    engine.areg2 = arg2;
    engine.areg3 = arg3;
    engine.areg4 = arg4;

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Iterator<PatchListEntry> iter = pl.getPatches().iterator();

    engine.areg5 = new JavaObjectTerm(iter);

    return engine.jtry5(commit_delta_check, commit_delta_next);
  }

  private static final class PRED_commit_delta_check extends Operation {
    @Override
    public Operation exec(Prolog engine) {
      Term a1 = engine.areg1;
      Term a2 = engine.areg2;
      Term a3 = engine.areg3;
      Term a4 = engine.areg4;
      Term a5 = engine.areg5;

      Pattern regex = (Pattern)((JavaObjectTerm)a1).object();
      Iterator<PatchListEntry> iter =
        (Iterator<PatchListEntry>)((JavaObjectTerm)a5).object();
      if (iter.hasNext()) {
        PatchListEntry patch = iter.next();
        String newName = patch.getNewName();
        String oldName = patch.getOldName();
        Patch.ChangeType changeType = patch.getChangeType();

        if (regex.matcher(newName).matches() ||
            (oldName != null && regex.matcher(oldName).matches())) {
          SymbolTerm changeSym = getTypeSymbol(changeType);
          SymbolTerm newSym = SymbolTerm.create(newName);
          SymbolTerm oldSym = Prolog.Nil;
          if (oldName != null) {
            oldSym = SymbolTerm.create(oldName);
          }

          if (!a2.unify(changeSym, engine.trail)) {
            return engine.fail();
          }
          if (!a3.unify(newSym, engine.trail)) {
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
      return engine.trust(commit_delta_empty);
    }
  }

  private static final class PRED_commit_delta_empty extends Operation {
    @Override
    public Operation exec(Prolog engine) {
      Term a5 = engine.areg5;

      Iterator<PatchListEntry> iter =
        (Iterator<PatchListEntry>)((JavaObjectTerm)a5).object();
      if (!iter.hasNext()) {
        return engine.fail();
      }

      return engine.jtry5(commit_delta_check, commit_delta_next);
    }
  }

  private static SymbolTerm getTypeSymbol(Patch.ChangeType type) {
    switch (type) {
      case ADDED:
        return add;
      case MODIFIED:
        return modify;
      case DELETED:
        return delete;
      case RENAMED:
        return rename;
      case COPIED:
        return copy;
    }
    throw new IllegalArgumentException("ChangeType not recognized");
  }
}