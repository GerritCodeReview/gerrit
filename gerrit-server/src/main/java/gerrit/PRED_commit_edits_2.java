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
 * Returns true if any of the files that match FileNameRegex have edited lines that match EditRegex
 *
 * <pre>
 *   'commit_edits'(+FileNameRegex, +EditRegex)
 * </pre>
 */
public class PRED_commit_edits_2 extends Predicate.P2 {
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

    Pattern fileRegex = getRegexParameter(a1);
    Pattern editRegex = getRegexParameter(a2);

    PatchList pl = StoredValues.PATCH_LIST.get(engine);
    Repository repo = StoredValues.REPOSITORY.get(engine);

    try (ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      final RevTree aTree;
      final RevTree bTree;
      final RevCommit bCommit = rw.parseCommit(pl.getNewId());

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
          for (Edit edit : edits) {
            if (tA != Text.EMPTY) {
              String aDiff = tA.getString(edit.getBeginA(), edit.getEndA(), true);
              if (editRegex.matcher(aDiff).find()) {
                return cont;
              }
            }
            if (tB != Text.EMPTY) {
              String bDiff = tB.getString(edit.getBeginB(), edit.getEndB(), true);
              if (editRegex.matcher(bDiff).find()) {
                return cont;
              }
            }
          }
        }
      }
    } catch (IOException err) {
      throw new JavaException(this, 1, err);
    }

    return engine.fail();
  }

  private Pattern getRegexParameter(Term term) {
    if (term instanceof VariableTerm) {
      throw new PInstantiationException(this, 1);
    }
    if (!(term instanceof SymbolTerm)) {
      throw new IllegalTypeException(this, 1, "symbol", term);
    }
    return Pattern.compile(term.name(), Pattern.MULTILINE);
  }

  private Text load(final ObjectId tree, final String path, final ObjectReader reader)
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
