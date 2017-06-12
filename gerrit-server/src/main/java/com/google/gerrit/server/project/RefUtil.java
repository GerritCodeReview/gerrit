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

package com.google.gerrit.server.project;

import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import java.io.IOException;
import java.util.Collections;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefUtil {
  private static final Logger log = LoggerFactory.getLogger(RefUtil.class);

  public static ObjectId parseBaseRevision(
      Repository repo, Project.NameKey projectName, String baseRevision)
      throws InvalidRevisionException {
    try {
      ObjectId revid = repo.resolve(baseRevision);
      if (revid == null) {
        throw new InvalidRevisionException();
      }
      return revid;
    } catch (IOException err) {
      log.error(
          "Cannot resolve \"" + baseRevision + "\" in project \"" + projectName.get() + "\"", err);
      throw new InvalidRevisionException();
    } catch (RevisionSyntaxException err) {
      log.error("Invalid revision syntax \"" + baseRevision + "\"", err);
      throw new InvalidRevisionException();
    }
  }

  public static RevWalk verifyConnected(Repository repo, ObjectId revid)
      throws InvalidRevisionException {
    try {
      ObjectWalk rw = new ObjectWalk(repo);
      try {
        rw.markStart(rw.parseCommit(revid));
      } catch (IncorrectObjectTypeException err) {
        throw new InvalidRevisionException();
      }
      RefDatabase refDb = repo.getRefDatabase();
      Iterable<Ref> refs =
          Iterables.concat(
              refDb.getRefs(Constants.R_HEADS).values(), refDb.getRefs(Constants.R_TAGS).values());
      Ref rc = refDb.exactRef(RefNames.REFS_CONFIG);
      if (rc != null) {
        refs = Iterables.concat(refs, Collections.singleton(rc));
      }
      for (Ref r : refs) {
        try {
          rw.markUninteresting(rw.parseAny(r.getObjectId()));
        } catch (MissingObjectException err) {
          continue;
        }
      }
      rw.checkConnectivity();
      return rw;
    } catch (IncorrectObjectTypeException | MissingObjectException err) {
      throw new InvalidRevisionException();
    } catch (IOException err) {
      log.error(
          "Repository \"" + repo.getDirectory() + "\" may be corrupt; suggest running git fsck",
          err);
      throw new InvalidRevisionException();
    }
  }

  public static String getRefPrefix(String refName) {
    int i = refName.lastIndexOf('/');
    if (i > Constants.R_HEADS.length() - 1) {
      return refName.substring(0, i);
    }
    return Constants.R_HEADS;
  }

  /** Error indicating the revision is invalid as supplied. */
  static class InvalidRevisionException extends Exception {
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Invalid Revision";

    InvalidRevisionException() {
      super(MESSAGE);
    }
  }
}
