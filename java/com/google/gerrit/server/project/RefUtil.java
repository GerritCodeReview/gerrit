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

import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
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

public class RefUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private RefUtil() {}

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
      logger.atSevere().withCause(err).log(
          "Cannot resolve \"%s\" in project \"%s\"", baseRevision, projectName.get());
      throw new InvalidRevisionException();
    } catch (RevisionSyntaxException err) {
      logger.atSevere().withCause(err).log("Invalid revision syntax \"%s\"", baseRevision);
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
              refDb.getRefsByPrefix(Constants.R_HEADS), refDb.getRefsByPrefix(Constants.R_TAGS));
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
      logger.atSevere().withCause(err).log(
          "Repository \"%s\" may be corrupt; suggest running git fsck", repo.getDirectory());
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

  public static String normalizeTagRef(String tag) throws BadRequestException {
    String result = tag;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    if (result.startsWith(R_REFS) && !result.startsWith(R_TAGS)) {
      throw new BadRequestException("invalid tag name \"" + result + "\"");
    }
    if (!result.startsWith(R_TAGS)) {
      result = R_TAGS + result;
    }
    if (!Repository.isValidRefName(result)) {
      throw new BadRequestException("invalid tag name \"" + result + "\"");
    }
    return result;
  }

  /** Error indicating the revision is invalid as supplied. */
  public static class InvalidRevisionException extends Exception {
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "Invalid Revision";

    InvalidRevisionException() {
      super(MESSAGE);
    }
  }
}
