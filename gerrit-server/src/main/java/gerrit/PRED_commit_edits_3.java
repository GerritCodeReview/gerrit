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

import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.Text;
import com.googlecode.prolog_cafe.exceptions.IllegalDomainException;
import com.googlecode.prolog_cafe.exceptions.IllegalTypeException;
import com.googlecode.prolog_cafe.exceptions.JavaException;
import com.googlecode.prolog_cafe.exceptions.PInstantiationException;
import com.googlecode.prolog_cafe.exceptions.PrologException;
import com.googlecode.prolog_cafe.lang.Operation;
import com.googlecode.prolog_cafe.lang.Predicate;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Returns true if "any of" or "all" the files that match FileNameRegex
 * have edited lines that match EditRegex. Qualifier can assume
 * only one of the following values:
 *
 * "all_of": EditRegex must match with all files that match FileNameRegex
 *
 * "any_of": EditRegex must match at least with one of the files that
 *           matches FileNameRegex
 *
 * <pre>
 *   'commit_edits'(+FileNameRegex, +EditRegex, +Qualifier)
 * </pre>
 */
public class PRED_commit_edits_3 extends Predicate.P3 {
  private enum MatchResult {
    Ok,
    Pass,
    Fail
  }

  public PRED_commit_edits_3(Term a1, Term a2, Term a3, Operation n) {
    arg1 = a1;
    arg2 = a2;
    arg3 = a3;
    cont = n;
  }

  @Override
  public Operation exec(Prolog engine) throws PrologException {
    engine.setB0();

    Term a1 = arg1.dereference();
    Term a2 = arg2.dereference();

    Pattern fileRegex = getRegexParameter(a1);
    Pattern editRegex = getRegexParameter(a2);

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Repository repo = StoredValues.REPOSITORY.get(engine);

    try (ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      final RevTree aTree;
      final RevTree bTree;
      final RevCommit bCommit = rw.parseCommit(pl.getNewId());
      final boolean allOf = isAllOf(arg3.dereference());

      if (pl.getOldId() != null) {
        aTree = rw.parseTree(pl.getOldId());
      } else {
        // Octopus merge with unknown automatic merge result, since the
        // web UI returns no files to match against, just fail.
        return engine.fail();
      }
      bTree = bCommit.getTree();

      for (PatchListEntry entry : pl.getPatches()) {
        String newName = entry.getNewName();
        String oldName = entry.getOldName();

        if (newName.equals("/COMMIT_MSG")) {
          continue;
        }

        if (fileRegex.matcher(newName).find()
            || (oldName != null && fileRegex.matcher(oldName).find())) {
          List<Edit> edits = entry.getEdits();

          if (edits.isEmpty()) {
            continue;
          }
          Text tA;
          if (oldName != null) {
            tA = load(aTree, oldName, reader);
          } else {
            tA = load(aTree, newName, reader);
          }
          Text tB = load(bTree, newName, reader);

          int editIdx;
          for (editIdx = 0; editIdx < edits.size(); ++editIdx) {
            final Edit edit = edits.get(editIdx);

            final MatchResult mrA = match(edit.getBeginA(), edit.getEndA(), tA, editRegex, allOf);
            if (mrA == MatchResult.Ok) {
              return cont;
            }

            final MatchResult mrB = match(edit.getBeginB(), edit.getEndB(), tB, editRegex, allOf);
            if (mrB == MatchResult.Ok) {
              return cont;
            }

            if (mrA == MatchResult.Fail || mrB == MatchResult.Fail) {
              break;
            }
          }

          if (editIdx == edits.size()) {
            return cont;
          }
        }
      }
    } catch (IOException err) {
      throw new JavaException(this, 1, err);
    }

    return engine.fail();
  }

  private MatchResult match(int startSeq, int endSeq, Text t, Pattern regex, boolean allOf) {
    if (t != Text.EMPTY) {
      String diff = t.getString(startSeq, endSeq, true);
      if (regex.matcher(diff).find()) {
        if (!allOf) {
          return MatchResult.Ok;
        }
      } else {
        if (allOf) {
          return MatchResult.Fail;
        }
      }
    }

    return MatchResult.Pass;
  }

  private void checkIsASymbolTerm(Term term) {
    if (term instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!(term instanceof SymbolTerm)) {
      throw new IllegalTypeException(this, 1, "symbol", term);
    }
  }

  private Pattern getRegexParameter(Term term) {
    checkIsASymbolTerm(term);

    return Pattern.compile(term.name(), Pattern.MULTILINE);
  }

  private boolean isAllOf(Term term) {
    checkIsASymbolTerm(term);

    if (term.name() != "all_of" && term.name() != "any_of")
      throw new IllegalDomainException(this, 3, "[any_of, all_of]", term);

    return term.name() == "all_of";
  }

  private Text load(ObjectId tree, String path, ObjectReader reader)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
          IOException {
    if (path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(reader, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
      return Text.EMPTY;
    }
    return new Text(reader.open(tw.getObjectId(0), Constants.OBJ_BLOB));
  }
}
