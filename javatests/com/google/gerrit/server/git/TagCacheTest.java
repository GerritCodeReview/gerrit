// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.ForwardingCache;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.git.TagCache.Implementation;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class TagCacheTest {
  @Test
  public void implementationDefaultsToBitmap() {
    assertThat(TagCache.implementation(new Config())).isEqualTo(Implementation.BITMAP);
  }

  @Test
  public void implementationIgnoresCase() {
    assertThat(TagCache.implementation(config("dag"))).isEqualTo(Implementation.DAG);
    assertThat(TagCache.implementation(config("DAG"))).isEqualTo(Implementation.DAG);
    assertThat(TagCache.implementation(config("Bitmap"))).isEqualTo(Implementation.BITMAP);
  }

  @Test
  public void unknownImplementationIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> TagCache.implementation(config("graph")));
  }

  @Test
  public void bitmapAgreesWithRevWalkOnRandomHistories() throws Exception {
    agreesWithRevWalkOnRandomHistories(
        () -> new BitmapTagCache(persisting(TagSetHolder.Serializer.INSTANCE)));
  }

  @Test
  public void dagAgreesWithRevWalkOnRandomHistories() throws Exception {
    agreesWithRevWalkOnRandomHistories(
        () -> new DagTagCache(persisting(DagTagSetHolder.Serializer.INSTANCE)));
  }

  /**
   * Grows random histories (with merges and unrelated roots) and moves, creates and deletes
   * branches and tags at random. After each round, every branch must reach exactly the tags that a
   * RevWalk from it reaches. The cache serializes the TagSets it is given, as on disk.
   */
  private static void agreesWithRevWalkOnRandomHistories(Supplier<TagCache> newCache)
      throws Exception {
    Project.NameKey project = Project.nameKey("project");
    for (int seed = 0; seed < 200; seed++) {
      Random rnd = new Random(seed);
      InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("project"));
      TagCache cache = newCache.get();
      try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
        List<RevCommit> commits = new ArrayList<>();
        commits.add(tr.commit().create());
        for (int round = 0; round < 8; round++) {
          for (int i = 0; i < 4; i++) {
            int kind = rnd.nextInt(10);
            RevCommit p1 = commits.get(rnd.nextInt(commits.size()));
            RevCommit p2 = commits.get(rnd.nextInt(commits.size()));
            commits.add(
                kind == 0
                    ? tr.commit().create()
                    : kind < 3
                        ? tr.commit().parent(p1).parent(p2).create()
                        : tr.commit().parent(p1).create());
          }
          for (int i = 0; i < 5; i++) {
            // Mostly recent commits, so that refs move onto new history between lookups.
            int recent = Math.min(commits.size(), 6);
            RevCommit target =
                rnd.nextBoolean()
                    ? commits.get(commits.size() - 1 - rnd.nextInt(recent))
                    : commits.get(rnd.nextInt(commits.size()));
            String name = "refs/heads/b" + rnd.nextInt(6);
            Ref existing = repo.exactRef(name);
            List<Ref> branches = repo.getRefDatabase().getRefsByPrefix("refs/heads/");
            switch (rnd.nextInt(6)) {
              case 0 -> tr.update(name, target);
              case 1 -> {
                // Fast-forward, possibly over tagged commits.
                if (existing != null) {
                  RevCommit tip = tr.getRevWalk().parseCommit(existing.getObjectId());
                  List<RevCommit> descendants = new ArrayList<>();
                  for (RevCommit c : commits) {
                    if (!c.equals(tip) && tr.getRevWalk().isMergedInto(tip, c)) {
                      descendants.add(c);
                    }
                  }
                  if (!descendants.isEmpty()) {
                    tr.update(name, descendants.get(rnd.nextInt(descendants.size())));
                  }
                }
              }
              case 2 -> {
                // A new branch at the tip of another one.
                if (!branches.isEmpty()) {
                  Ref from = branches.get(rnd.nextInt(branches.size()));
                  tr.update(name, tr.getRevWalk().parseCommit(from.getObjectId()));
                }
              }
              case 3, 4 -> tr.update("refs/tags/t" + rnd.nextInt(10), tr.tag("t", target));
              default -> {
                List<Ref> refs = repo.getRefDatabase().getRefs();
                if (!refs.isEmpty()) {
                  tr.delete(refs.get(rnd.nextInt(refs.size())).getName());
                }
              }
            }
          }
          List<Ref> tags = repo.getRefDatabase().getRefsByPrefix("refs/tags/");
          List<Ref> branches = repo.getRefDatabase().getRefsByPrefix("refs/heads/");
          try (RevWalk rw = new RevWalk(repo)) {
            // First one lookup that sees all branches, so that it finds several refs moved at once.
            TagCache.Matcher all = cache.matcher(project, repo, branches, tags);
            for (Ref tag : tags) {
              RevCommit tagged =
                  rw.parseCommit(repo.getRefDatabase().peel(tag).getPeeledObjectId());
              boolean expected = false;
              for (Ref branch : branches) {
                expected |= rw.isMergedInto(tagged, rw.parseCommit(branch.getObjectId()));
              }
              assertWithMessage(
                      "seed %s round %s all branches reach %s", seed, round, tag.getName())
                  .that(all.isReachable(tag))
                  .isEqualTo(expected);
            }
            for (Ref branch : branches) {
              TagCache.Matcher m = cache.matcher(project, repo, List.of(branch), tags);
              RevCommit tip = rw.parseCommit(branch.getObjectId());
              for (Ref tag : tags) {
                RevCommit tagged =
                    rw.parseCommit(repo.getRefDatabase().peel(tag).getPeeledObjectId());
                assertWithMessage(
                        "seed %s round %s %s reaches %s",
                        seed, round, branch.getName(), tag.getName())
                    .that(m.isReachable(tag))
                    .isEqualTo(rw.isMergedInto(tagged, tip));
              }
            }
          }
        }
      }
    }
  }

  /**
   * A cache that keeps what a persistent cache would have written on put, so that lookups also work
   * on TagSets read back from disk.
   */
  private static <V> Cache<String, V> persisting(CacheSerializer<V> serializer) {
    return new ForwardingCache.SimpleForwardingCache<String, V>(CacheBuilder.newBuilder().build()) {
      @Override
      public void put(String key, V value) {
        super.put(key, serializer.deserialize(serializer.serialize(value)));
      }
    };
  }

  private static Config config(String implementation) {
    Config cfg = new Config();
    cfg.setString("cache", "git_tags", "implementation", implementation);
    return cfg;
  }
}
