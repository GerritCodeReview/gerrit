// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PATCH_SET;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.InsertedObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Commit implementation with some optimizations for change notes parsing.
 *
 * <ul>
 *   <li>Caches the result of {@link #getFooterLines()}, which is otherwise very wasteful with
 *       allocations.
 * </ul>
 */
public class ChangeNotesCommit extends RevCommit {

  /** A {@link RevWalk} producing {@link ChangeNotesCommit}s. */
  public static ChangeNotesRevWalk newRevWalk(Repository repo) {
    return new ChangeNotesRevWalk(repo);
  }

  public static ChangeNotesRevWalk newStagedRevWalk(
      Repository repo, Iterable<InsertedObject> stagedObjs) {
    final InMemoryInserter ins = new InMemoryInserter(repo);
    for (InsertedObject obj : stagedObjs) {
      ins.insert(obj);
    }
    return new ChangeNotesRevWalk(ins.newReader()) {
      @Override
      public void close() {
        ins.close();
        super.close();
      }
    };
  }

  /** A {@link RevWalk} that creates {@link ChangeNotesCommit}s rather than {@link RevCommit}s */
  public static class ChangeNotesRevWalk extends RevWalk {
    private ChangeNotesRevWalk(Repository repo) {
      super(repo);
    }

    private ChangeNotesRevWalk(ObjectReader reader) {
      super(reader);
    }

    @Override
    protected ChangeNotesCommit createCommit(AnyObjectId id) {
      return new ChangeNotesCommit(id);
    }

    @Override
    public ChangeNotesCommit next()
        throws MissingObjectException, IncorrectObjectTypeException, IOException {
      return (ChangeNotesCommit) super.next();
    }

    @Override
    public void markStart(RevCommit c)
        throws MissingObjectException, IncorrectObjectTypeException, IOException {
      checkArgument(c instanceof ChangeNotesCommit);
      super.markStart(c);
    }

    @Override
    public void markUninteresting(RevCommit c)
        throws MissingObjectException, IncorrectObjectTypeException, IOException {
      checkArgument(c instanceof ChangeNotesCommit);
      super.markUninteresting(c);
    }

    @Override
    public ChangeNotesCommit lookupCommit(AnyObjectId id) {
      return (ChangeNotesCommit) super.lookupCommit(id);
    }

    @Override
    public ChangeNotesCommit parseCommit(AnyObjectId id)
        throws MissingObjectException, IncorrectObjectTypeException, IOException {
      return (ChangeNotesCommit) super.parseCommit(id);
    }
  }

  private List<FooterLine> footerLines;

  public ChangeNotesCommit(AnyObjectId id) {
    super(id);
  }

  private void initFooterLines() {
    if (footerLines == null) {
      footerLines = getFooterLines();
    }
  }

  public List<String> getFooterLineValues(FooterKey key) {
    initFooterLines();
    if (footerLines.isEmpty()) {
      return ImmutableList.of();
    }
    String first = null;
    List<String> r = null;
    for (FooterLine fl : footerLines) {
      if (fl.matches(key)) {
        if (first == null) {
          first = fl.getValue();
        } else {
          if (r == null) {
            r = new ArrayList<>(2);
            r.add(first);
          }
          r.add(fl.getValue());
        }
      }
    }
    if (r != null) {
      return r;
    }
    if (first != null) {
      return ImmutableList.of(first);
    }
    return ImmutableList.of();
  }

  public boolean isAttentionSetCommitOnly(boolean hasChangeMessage) {
    if (hasChangeMessage) {
      return false;
    }
    initFooterLines();
    if (footerLines.size() < 2) {
      return false;
    }
    boolean hasPatchSet = false;
    boolean hasAttention = false;
    for (FooterLine fl : footerLines) {
      if (fl.matches(FOOTER_PATCH_SET)) {
        hasPatchSet = true;
      } else if (fl.matches(FOOTER_ATTENTION)) {
        hasAttention = true;
      } else {
        return false;
      }
    }
    return hasPatchSet && hasAttention;
  }
}
