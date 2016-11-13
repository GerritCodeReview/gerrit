// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import static java.util.Comparator.comparingInt;

import com.google.common.collect.Ordering;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ChangeUtil {
  private static final Object uuidLock = new Object();
  private static final int SEED = 0x2418e6f9;
  private static int uuidPrefix;
  private static int uuidSeq;

  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";
  private static final int SUBJECT_CROP_RANGE = 10;

  public static final Ordering<PatchSet> PS_ID_ORDER =
      Ordering.from(comparingInt(PatchSet::getPatchSetId));

  /**
   * Generate a new unique identifier for change message entities.
   *
   * @param db the database connection, used to increment the change message allocation sequence.
   * @return the new unique identifier.
   * @throws OrmException the database couldn't be incremented.
   */
  public static String messageUUID(ReviewDb db) throws OrmException {
    int p;
    int s;
    synchronized (uuidLock) {
      if (uuidSeq == 0) {
        uuidPrefix = db.nextChangeMessageId();
        uuidSeq = Integer.MAX_VALUE;
      }
      p = uuidPrefix;
      s = uuidSeq--;
    }
    String u = IdGenerator.format(IdGenerator.mix(SEED, p));
    String l = IdGenerator.format(IdGenerator.mix(p, s));
    return u + '_' + l;
  }

  public static PatchSet.Id nextPatchSetId(Map<String, Ref> allRefs, PatchSet.Id id) {
    PatchSet.Id next = nextPatchSetId(id);
    while (allRefs.containsKey(next.toRefName())) {
      next = nextPatchSetId(next);
    }
    return next;
  }

  public static PatchSet.Id nextPatchSetId(PatchSet.Id id) {
    return new PatchSet.Id(id.getParentKey(), id.get() + 1);
  }

  public static PatchSet.Id nextPatchSetId(Repository git, PatchSet.Id id) throws IOException {
    return nextPatchSetId(git.getRefDatabase().getRefs(RefDatabase.ALL), id);
  }

  public static String cropSubject(String subject) {
    if (subject.length() > SUBJECT_MAX_LENGTH) {
      int maxLength = SUBJECT_MAX_LENGTH - SUBJECT_CROP_APPENDIX.length();
      for (int cropPosition = maxLength;
          cropPosition > maxLength - SUBJECT_CROP_RANGE;
          cropPosition--) {
        if (Character.isWhitespace(subject.charAt(cropPosition - 1))) {
          return subject.substring(0, cropPosition) + SUBJECT_CROP_APPENDIX;
        }
      }
      return subject.substring(0, maxLength) + SUBJECT_CROP_APPENDIX;
    }
    return subject;
  }

  private ChangeUtil() {}
}
