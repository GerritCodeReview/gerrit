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
import com.google.common.io.BaseEncoding;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ChangeUtil {
  private static final Random UUID_RANDOM = new SecureRandom();
  private static final BaseEncoding UUID_ENCODING = BaseEncoding.base16().lowerCase();

  private static final int SUBJECT_MAX_LENGTH = 80;
  private static final String SUBJECT_CROP_APPENDIX = "...";
  private static final int SUBJECT_CROP_RANGE = 10;

  public static final Ordering<PatchSet> PS_ID_ORDER =
      Ordering.from(comparingInt(PatchSet::getPatchSetId));

  public static String formatChangeUrl(String canonicalWebUrl, Change change) {
    return canonicalWebUrl + change.getChangeId();
  }

  /** @return a new unique identifier for change message entities. */
  public static String messageUuid() {
    byte[] buf = new byte[8];
    UUID_RANDOM.nextBytes(buf);
    return UUID_ENCODING.encode(buf, 0, 4) + '_' + UUID_ENCODING.encode(buf, 4, 4);
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
