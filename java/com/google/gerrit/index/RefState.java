// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@AutoValue
public abstract class RefState {
  public static SetMultimap<Project.NameKey, RefState> parseStates(Iterable<byte[]> states) {
    RefState.check(states != null, null);
    SetMultimap<Project.NameKey, RefState> result =
        MultimapBuilder.hashKeys().hashSetValues().build();
    for (byte[] b : states) {
      RefState.check(b != null, null);
      String s = new String(b, UTF_8);
      List<String> parts = Splitter.on(':').splitToList(s);
      RefState.check(parts.size() == 3 && !parts.get(0).isEmpty() && !parts.get(1).isEmpty(), s);
      result.put(Project.nameKey(parts.get(0)), RefState.create(parts.get(1), parts.get(2)));
    }
    return result;
  }

  public static RefState create(String ref, String sha) {
    return new AutoValue_RefState(ref, ObjectId.fromString(sha));
  }

  public static RefState create(String ref, @Nullable ObjectId id) {
    return new AutoValue_RefState(ref, firstNonNull(id, ObjectId.zeroId()));
  }

  public static RefState of(Ref ref) {
    return new AutoValue_RefState(ref.getName(), ref.getObjectId());
  }

  public byte[] toByteArray(Project.NameKey project) {
    byte[] a = (project.toString() + ':' + ref() + ':').getBytes(UTF_8);
    byte[] b = new byte[a.length + Constants.OBJECT_ID_STRING_LENGTH];
    System.arraycopy(a, 0, b, 0, a.length);
    id().copyTo(b, a.length);
    return b;
  }

  public static void check(boolean condition, String str) {
    checkArgument(condition, "invalid RefState: %s", str);
  }

  public abstract String ref();

  public abstract ObjectId id();

  public boolean match(Repository repo) throws IOException {
    Ref ref = repo.exactRef(ref());
    ObjectId expected = ref != null ? ref.getObjectId() : ObjectId.zeroId();
    return id().equals(expected);
  }
}
