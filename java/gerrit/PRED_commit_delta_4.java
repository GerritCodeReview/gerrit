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

import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.rules.prolog.StoredValues;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
import com.googlecode.prolog_cafe.exceptions.JavaException;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Given a regular expression, checks it against the file list in the most recent patchset of a
 * change. For all files that match the regex, returns the (new) path of the file, the change type,
 * and the old path of the file if applicable (if the file was copied or renamed).
 *
 * <pre>
 *   'commit_delta'(+Regex, -ChangeType, -NewPath, -OldPath)
 * </pre>
 */
public class PRED_commit_delta_4 extends Predicate.P4 {
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
    if (a1 instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!(a1 instanceof SymbolTerm)) {
      throw new IllegalTypeException(this, 1, "symbol", a1);
    }
    Pattern regex = Pattern.compile(a1.name());
    engine.r1 = new JavaObjectTerm(regex);
    engine.r2 = arg2;
    engine.r3 = arg3;
    engine.r4 = arg4;

    Repository repository = StoredValues.REPOSITORY.get(engine);

    try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      diffFormatter.setRepository(repository);
      // Do not detect renames; that would require reading file contents, which is slow for large
      // files.
      RevCommit commit = StoredValues.COMMIT.get(engine);
      List<DiffEntry> diffEntries =
          diffFormatter.scan(
              // In case of a merge commit, i.e. >1 parents, we use parent #0 by convention. So
              // parent #0 is always the right choice, if it exists.
              commit.getParentCount() > 0 ? commit.getParent(0) : null, commit);
      engine.r5 = new JavaObjectTerm(diffEntries.iterator());
    } catch (IOException e) {
      throw new JavaException(e);
    }

    return engine.jtry5(commit_delta_check, commit_delta_next);
  }

  private static final class PRED_commit_delta_check extends Operation {
    @Override
    public Operation exec(Prolog engine) {
      Term a1 = engine.r1;
      Term a2 = engine.r2;
      Term a3 = engine.r3;
      Term a4 = engine.r4;
      Term a5 = engine.r5;

      Pattern regex = (Pattern) ((JavaObjectTerm) a1).object();
      @SuppressWarnings("unchecked")
      Iterator<DiffEntry> iter = (Iterator<DiffEntry>) ((JavaObjectTerm) a5).object();
      while (iter.hasNext()) {
        DiffEntry diffEntry = iter.next();
        String newName = diffEntry.getNewPath();
        String oldName = diffEntry.getOldPath();
        DiffEntry.ChangeType changeType = diffEntry.getChangeType();

        if ((!isNull(newName) && regex.matcher(newName).find())
            || (!isNull(oldName) && regex.matcher(oldName).find())) {
          SymbolTerm changeSym = getTypeSymbol(changeType);
          SymbolTerm newSym = isNull(newName) ? Prolog.Nil : SymbolTerm.create(newName);
          SymbolTerm oldSym = isNull(oldName) ? Prolog.Nil : SymbolTerm.create(oldName);
          // For compatibility with legacy semantics:
          if (changeSym.equals(delete)) {
            newSym = oldSym;
            oldSym = Prolog.Nil;
          }

          if (!a2.unify(changeSym, engine.trail)) {
            continue;
          }
          if (!a3.unify(newSym, engine.trail)) {
            continue;
          }
          if (!a4.unify(oldSym, engine.trail)) {
            continue;
          }
          return engine.cont;
        }
      }
      return engine.fail();
    }
  }

  private static boolean isNull(String path) {
    return path.equals("/dev/null");
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
      Term a5 = engine.r5;

      @SuppressWarnings("unchecked")
      Iterator<PatchListEntry> iter = (Iterator<PatchListEntry>) ((JavaObjectTerm) a5).object();
      if (!iter.hasNext()) {
        return engine.fail();
      }

      return engine.jtry5(commit_delta_check, commit_delta_next);
    }
  }

  private static SymbolTerm getTypeSymbol(DiffEntry.ChangeType type) {
    switch (type) {
      case ADD:
        return add;
      case MODIFY:
        return modify;
      case DELETE:
        return delete;
      case RENAME:
        return rename;
      case COPY:
        return copy;
    }
    throw new IllegalArgumentException("ChangeType not recognized");
  }
}
