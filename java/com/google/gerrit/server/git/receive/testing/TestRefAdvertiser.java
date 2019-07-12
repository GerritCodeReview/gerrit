// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.git.receive.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefAdvertiser;

/** Helper to collect advertised refs and additonal haves and verify them in tests. */
public class TestRefAdvertiser extends RefAdvertiser {

  @VisibleForTesting
  @AutoValue
  public abstract static class Result {
    public abstract Map<String, Ref> allRefs();

    public abstract Set<ObjectId> additionalHaves();

    public static Result create(Map<String, Ref> allRefs, Set<ObjectId> additionalHaves) {
      return new AutoValue_TestRefAdvertiser_Result(allRefs, additionalHaves);
    }
  }

  private final Map<String, Ref> advertisedRefs;
  private final Set<ObjectId> additionalHaves;
  private final Repository repo;

  public TestRefAdvertiser(Repository repo) {
    advertisedRefs = new HashMap<>();
    additionalHaves = new HashSet<>();
    this.repo = repo;
  }

  @Override
  protected void writeOne(CharSequence line) throws IOException {
    List<String> lineParts =
        StreamSupport.stream(Splitter.on(' ').split(line).spliterator(), false)
            .map(String::trim)
            .collect(toImmutableList());
    if (".have".equals(lineParts.get(1))) {
      additionalHaves.add(ObjectId.fromString(lineParts.get(0)));
    } else {
      ObjectId id = ObjectId.fromString(lineParts.get(0));
      Ref ref =
          repo.getRefDatabase()
              .getRefs()
              .stream()
              .filter(r -> r.getObjectId().equals(id))
              .findAny()
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          line.toString() + " does not conform to expected pattern"));
      advertisedRefs.put(lineParts.get(1), ref);
    }
  }

  @Override
  protected void end() {}

  public Result result() {
    return Result.create(advertisedRefs, additionalHaves);
  }
}
