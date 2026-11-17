// Copyright (C) 2018 The Android Open Source Project
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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;
import static org.eclipse.jgit.lib.ObjectReader.OBJ_ANY;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.CachedRefProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.TagProto;
import com.google.gerrit.server.git.TagSet.CachedRef;
import com.google.gerrit.server.git.TagSet.Tag;
import com.google.inject.TypeLiteral;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.roaringbitmap.RoaringBitmap;

public class TagSetTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void roundTripToProto() {
    HashMap<String, CachedRef> refs = new HashMap<>();
    refs.put(
        "refs/heads/master",
        new CachedRef(1, ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
    refs.put(
        "refs/heads/branch",
        new CachedRef(2, ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    ObjectIdOwnerMap<Tag> tags = new ObjectIdOwnerMap<>();
    tags.add(
        new Tag(
            ObjectId.fromString("cccccccccccccccccccccccccccccccccccccccc"),
            RoaringBitmap.bitmapOf(1, 3, 5)));
    tags.add(
        new Tag(
            ObjectId.fromString("dddddddddddddddddddddddddddddddddddddddd"),
            RoaringBitmap.bitmapOf(2, 4, 6)));
    TagSet tagSet = new TagSet(Project.nameKey("project"), refs, tags);

    TagSetProto proto = tagSet.toProto();
    assertThat(proto)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            TagSetProto.newBuilder()
                .setProjectName("project")
                .putRef(
                    "refs/heads/master",
                    CachedRefProto.newBuilder()
                        .setId(
                            byteString(
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa))
                        .setFlag(1)
                        .build())
                .putRef(
                    "refs/heads/branch",
                    CachedRefProto.newBuilder()
                        .setId(
                            byteString(
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb))
                        .setFlag(2)
                        .build())
                .addTag(
                    TagProto.newBuilder()
                        .setId(
                            byteString(
                                0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc,
                                0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc))
                        .setFlags(
                            ByteString.copyFromUtf8(
                                ":0\000\000\001\000\000\000\000\000\002\000\020\000\000\000\001\000\003\000\005\000"))
                        .build())
                .addTag(
                    TagProto.newBuilder()
                        .setId(
                            byteString(
                                0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd,
                                0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd, 0xdd))
                        .setFlags(
                            ByteString.copyFromUtf8(
                                ":0\000\000\001\000\000\000\000\000\002\000\020\000\000\000\002\000\004\000\006\000"))
                        .build())
                .build());

    assertEqual(tagSet, TagSet.fromProto(proto));
  }

  @Test
  public void buildParsesCommitsFromCommitGraph() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    FileRepository repo =
        new FileRepository(temporaryFolder.newFolder("project.git")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                reads.incrementAndGet();
                return super.open(objectId, typeHint);
              }

              @Override
              public Optional<CommitGraph> getCommitGraph() throws IOException {
                return delegate.getCommitGraph();
              }
            };
          }
        };
    repo.create(true);
    try (TestRepository<FileRepository> tr = new TestRepository<>(repo)) {
      RevCommit c1 = tr.commit().create();
      RevCommit c2 = tr.commit().parent(c1).create();
      tr.update("refs/heads/master", tr.commit().parent(c2).create());
      tr.update("refs/tags/v1", c1);
    }
    StoredConfig cfg = repo.getConfig();
    cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, "commitGraph", true);
    cfg.setBoolean(
        ConfigConstants.CONFIG_GC_SECTION,
        null,
        ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH,
        true);
    cfg.save();
    new GC(repo).gc().get();

    reads.set(0);
    assertThat(new TagSet(Project.nameKey("project")).build(repo, null, null)).isTrue();
    // Only the branch and tag tips are read; the commit between them comes from the graph.
    assertThat(reads.get()).isEqualTo(2);
  }

  @Test
  public void buildStopsReadingObjectsWhenInterrupted() throws Exception {
    AtomicInteger interruptedReads = new AtomicInteger();
    // Mimics JGit's file-based object reader, which throws InterruptedIOException for every
    // object read once the current thread is interrupted.
    InMemoryRepository repo =
        new InMemoryRepository(new DfsRepositoryDescription("project")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                if (Thread.currentThread().isInterrupted()) {
                  interruptedReads.incrementAndGet();
                  throw new InterruptedIOException();
                }
                return super.open(objectId, typeHint);
              }
            };
          }
        };
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      for (int i = 0; i < 10; i++) {
        tr.update("refs/heads/branch" + i, tr.commit().parent(root).create());
      }
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }

    Thread.currentThread().interrupt();
    try {
      assertThat(new TagSet(Project.nameKey("project")).build(repo, null, null)).isFalse();
    } finally {
      Thread.interrupted();
    }

    // The first failed read must abort the build instead of failing (and logging) once per ref.
    assertThat(interruptedReads.get()).isEqualTo(1);
  }

  /**
   * Interrupts a build after all refs and tags were added but before reachability was fully
   * propagated, then checks that the incomplete TagSet was not cached and that the next build finds
   * the tag reachable. Guards against caching a TagSet in which the tag is wrongly hidden.
   */
  @Test
  public void tagSetInterruptedDuringHistoryWalkIsNotCached() throws Exception {
    AtomicReference<ObjectId> failOn = new AtomicReference<>();
    InMemoryRepository repo =
        new InMemoryRepository(new DfsRepositoryDescription("project")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                if (objectId.equals(failOn.get())) {
                  throw new InterruptedIOException();
                }
                return super.open(objectId, typeHint);
              }
            };
          }
        };
    Ref tagRef;
    RevCommit middle;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      middle = tr.commit().parent(root).create();
      tr.update("refs/heads/master", tr.commit().parent(middle).create());
      tr.update("refs/tags/v1", tr.tag("v1", root));
      tagRef = repo.exactRef("refs/tags/v1");
    }
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef("refs/heads/master"));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");

    // The middle commit is only read while propagating reachability through the history, i.e.
    // after all refs and tags have already been added to the TagSet.
    failOn.set(middle.copy());
    var unused = tagCache.get(project).matcher(tagCache, repo, include);
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNull();

    failOn.set(null);
    TagMatcher m = tagCache.get(project).matcher(tagCache, repo, include);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNotNull();
  }

  @Test
  public void buildAbortsAndDoesNotCacheOnStorageIoExceptionDuringRefSeeding() throws Exception {
    AtomicInteger failedReads = new AtomicInteger();
    AtomicBoolean failAllReads = new AtomicBoolean();
    AtomicReference<ObjectId> failOn = new AtomicReference<>();
    InMemoryRepository repo =
        new InMemoryRepository(new DfsRepositoryDescription("project")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                if (failAllReads.get() || objectId.equals(failOn.get())) {
                  failedReads.incrementAndGet();
                  throw new IOException("simulated storage error");
                }
                return super.open(objectId, typeHint);
              }
            };
          }
        };
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      for (int i = 0; i < 10; i++) {
        tr.update("refs/heads/branch" + i, tr.commit().parent(root).create());
      }
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    Project.NameKey project = Project.nameKey("project");
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef("refs/heads/branch0"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);

    failAllReads.set(true);
    assertThat(new TagSet(project).build(repo, null, null)).isFalse();
    assertThat(failedReads.get()).isEqualTo(1);

    // Cold build failure leaves TagSetHolder.tags null while m.newRefs is non-empty; matcher()
    // must not throw NullPointerException and must not cache the incomplete TagSet.
    failedReads.set(0);
    TagMatcher failedMatcher = tagCache.get(project).matcher(tagCache, repo, include);
    assertThat(failedMatcher).isNotNull();
    assertThat(failedReads.get()).isEqualTo(1);
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNull();

    // Storage IOException in addTag() during ref seeding must also abort build().
    failAllReads.set(false);
    failOn.set(root.copy());
    assertThat(new TagSet(project).build(repo, null, null)).isFalse();

    failOn.set(null);
    TagMatcher recoveredMatcher = tagCache.get(project).matcher(tagCache, repo, include);
    assertThat(recoveredMatcher.isReachable(tagRef)).isTrue();
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNotNull();
  }

  @Test
  public void tagSetIoExceptionDuringHistoryWalkIsNotCachedAndPreservesOldTagSetOnRebuild()
      throws Exception {
    AtomicReference<ObjectId> failOn = new AtomicReference<>();
    InMemoryRepository repo =
        new InMemoryRepository(new DfsRepositoryDescription("project")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                if (objectId.equals(failOn.get())) {
                  throw new IOException("simulated storage error");
                }
                return super.open(objectId, typeHint);
              }
            };
          }
        };
    Ref tagV1;
    RevCommit middle;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      middle = tr.commit().parent(root).create();
      tr.update("refs/heads/main", tr.commit().parent(middle).create());
      tr.update("refs/tags/v1", tr.tag("v1", root));
      tagV1 = repo.exactRef("refs/tags/v1");
    }
    ImmutableList<Ref> initialInclude = ImmutableList.of(repo.exactRef("refs/heads/main"));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");

    // Cold build failing during the history walk must not cache a truncated TagSet.
    failOn.set(middle.copy());
    TagMatcher coldFailed = tagCache.get(project).matcher(tagCache, repo, initialInclude);
    assertThat(coldFailed.isReachable(tagV1)).isFalse();
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNull();

    // Populate the cached TagSet with reachable v1.
    failOn.set(null);
    TagMatcher initialMatcher = tagCache.get(project).matcher(tagCache, repo, initialInclude);
    assertThat(initialMatcher.isReachable(tagV1)).isTrue();
    TagSet oldTagSet = guavaCache.getIfPresent(project.get()).getTagSet();
    assertThat(oldTagSet).isNotNull();

    // Add a branch at a new commit (forcing a full rebuild rather than fast refresh) and trigger
    // a transient IOException on middle during the rebuild's history walk.
    Ref tagV2;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit featureTip = tr.commit().parent(middle).create();
      tr.update("refs/heads/feature", featureTip);
      tr.update("refs/tags/v2", tr.tag("v2", featureTip));
      tagV2 = repo.exactRef("refs/tags/v2");
    }
    ImmutableList<Ref> rebuildInclude =
        ImmutableList.of(repo.exactRef("refs/heads/main"), repo.exactRef("refs/heads/feature"));

    failOn.set(middle.copy());
    TagMatcher failedRebuildMatcher = tagCache.get(project).matcher(tagCache, repo, rebuildInclude);
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isSameInstanceAs(oldTagSet);
    assertThat(failedRebuildMatcher.isReachable(tagV1)).isTrue();

    // Once the transient IOException clears, rebuild succeeds and caches the updated TagSet.
    failOn.set(null);
    TagMatcher recoveredMatcher = tagCache.get(project).matcher(tagCache, repo, rebuildInclude);
    TagSet rebuiltTagSet = guavaCache.getIfPresent(project.get()).getTagSet();
    assertThat(rebuiltTagSet).isNotNull();
    assertThat(rebuiltTagSet).isNotSameInstanceAs(oldTagSet);
    assertThat(recoveredMatcher.isReachable(tagV1)).isTrue();
    assertThat(recoveredMatcher.isReachable(tagV2)).isTrue();
  }

  @Test
  public void buildSkipsSingleDanglingRefWithMissingObjectException() throws Exception {
    try (FileRepository bareRepo =
        new FileRepository(temporaryFolder.newFolder("dangling-refs.git"))) {
      bareRepo.create(true);
      try (TestRepository<FileRepository> tr = new TestRepository<>(bareRepo)) {
        RevCommit root = tr.commit().create();
        tr.update("refs/heads/main", tr.commit().parent(root).create());
        tr.update("refs/tags/v1", tr.tag("v1", root));
      }

      Ref danglingBranch =
          new ObjectIdRef.Unpeeled(
              Ref.Storage.LOOSE,
              "refs/heads/dangling",
              ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
      Ref danglingTag =
          new ObjectIdRef.Unpeeled(
              Ref.Storage.LOOSE,
              "refs/tags/dangling-tag",
              ObjectId.fromString("cafebabecafebabecafebabecafebabecafebabe"));
      Repository repoWithDanglingRefs =
          new DelegateRepository(bareRepo) {
            @Override
            public RefDatabase getRefDatabase() {
              return new DelegateRefDatabase(bareRepo) {
                @Override
                public ImmutableList<Ref> getRefsByPrefixWithExclusions(
                    String include, Set<String> excludes) throws IOException {
                  return ImmutableList.<Ref>builder()
                      .addAll(super.getRefsByPrefixWithExclusions(include, excludes))
                      .add(danglingBranch)
                      .add(danglingTag)
                      .build();
                }
              };
            }
          };

      Project.NameKey project = Project.nameKey("project");
      TagSet tagSet = new TagSet(project);
      assertThat(tagSet.build(repoWithDanglingRefs, null, null)).isTrue();
      assertThat(tagSet.getRefsForTesting()).containsKey("refs/heads/main");
      assertThat(tagSet.getRefsForTesting()).doesNotContainKey("refs/heads/dangling");

      Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
      TagCache tagCache = new TagCache(guavaCache);
      ImmutableList<Ref> include = ImmutableList.of(bareRepo.exactRef("refs/heads/main"));
      TagMatcher m = tagCache.get(project).matcher(tagCache, repoWithDanglingRefs, include);
      assertThat(m.isReachable(bareRepo.exactRef("refs/tags/v1"))).isTrue();
      assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNotNull();
    }
  }

  @Test
  public void rebuildForNewTagsDiscoversNewTagOnUntaggedAncestorCommit() throws Exception {
    Project.NameKey project = Project.nameKey("project");

    // Case 1: m.newRefs is empty (existing branch unchanged while a new tag is created on a
    // previously untagged ancestor commit).
    InMemoryRepository repoEmptyNewRefs =
        new InMemoryRepository(new DfsRepositoryDescription("project"));
    RevCommit c1;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repoEmptyNewRefs)) {
      RevCommit root = tr.commit().create();
      c1 = tr.commit().parent(root).create();
      RevCommit tip = tr.commit().parent(c1).create();
      tr.update("refs/heads/main", tip);
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    TagCache cacheEmptyNewRefs = new TagCache(CacheBuilder.newBuilder().build());
    TagMatcher initial1 =
        cacheEmptyNewRefs
            .get(project)
            .matcher(
                cacheEmptyNewRefs,
                repoEmptyNewRefs,
                ImmutableList.of(repoEmptyNewRefs.exactRef("refs/heads/main")));
    assertThat(initial1.isReachable(repoEmptyNewRefs.exactRef("refs/tags/v1"))).isTrue();

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repoEmptyNewRefs)) {
      tr.update("refs/tags/v2", tr.tag("v2", c1));
    }
    TagMatcher m1 =
        cacheEmptyNewRefs
            .get(project)
            .matcher(
                cacheEmptyNewRefs,
                repoEmptyNewRefs,
                ImmutableList.of(repoEmptyNewRefs.exactRef("refs/heads/main")));
    assertThat(m1.newRefs).isEmpty();
    assertThat(m1.isReachable(repoEmptyNewRefs.exactRef("refs/tags/v2"))).isTrue();

    // Case 2: a new refs/heads/release shares tip with refs/heads/main (so TagSet.refresh copies
    // old.tags without scanning RefDatabase), and a new tag v2 points to untagged ancestor c1.
    InMemoryRepository repoSharedTip =
        new InMemoryRepository(new DfsRepositoryDescription("project"));
    RevCommit sharedC1;
    RevCommit sharedTip;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repoSharedTip)) {
      RevCommit root = tr.commit().create();
      sharedC1 = tr.commit().parent(root).create();
      sharedTip = tr.commit().parent(sharedC1).create();
      tr.update("refs/heads/main", sharedTip);
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    TagCache cacheSharedTip = new TagCache(CacheBuilder.newBuilder().build());
    TagMatcher initial2 =
        cacheSharedTip
            .get(project)
            .matcher(
                cacheSharedTip,
                repoSharedTip,
                ImmutableList.of(repoSharedTip.exactRef("refs/heads/main")));
    assertThat(initial2.isReachable(repoSharedTip.exactRef("refs/tags/v1"))).isTrue();

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repoSharedTip)) {
      tr.update("refs/heads/release", sharedTip);
      tr.update("refs/tags/v2", tr.tag("v2", sharedC1));
    }
    TagMatcher m2 =
        cacheSharedTip
            .get(project)
            .matcher(
                cacheSharedTip,
                repoSharedTip,
                ImmutableList.of(repoSharedTip.exactRef("refs/heads/release")));
    assertThat(m2.isReachable(repoSharedTip.exactRef("refs/tags/v2"))).isTrue();
  }

  @Test
  public void tagSetFields() {
    assertThatSerializedClass(TagSet.class)
        .hasFields(
            ImmutableMap.of(
                "projectName", Project.NameKey.class,
                "refs", new TypeLiteral<Map<String, CachedRef>>() {}.getType(),
                "tags", new TypeLiteral<ObjectIdOwnerMap<Tag>>() {}.getType()));
  }

  @Test
  public void cachedRefFields() {
    assertThatSerializedClass(CachedRef.class)
        .extendsClass(new TypeLiteral<AtomicReference<ObjectId>>() {}.getType());
    assertThatSerializedClass(CachedRef.class)
        .hasFields(
            ImmutableMap.of("flag", int.class, "value", new TypeLiteral<ObjectId>() {}.getType()));
  }

  @Test
  public void tagFields() {
    assertThatSerializedClass(Tag.class).extendsClass(ObjectIdOwnerMap.Entry.class);
    assertThatSerializedClass(Tag.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("refFlags", RoaringBitmap.class)
                .put("next", ObjectIdOwnerMap.Entry.class)
                .put("w1", int.class)
                .put("w2", int.class)
                .put("w3", int.class)
                .put("w4", int.class)
                .put("w5", int.class)
                .build());
  }

  // TODO(dborowitz): Find some more common place to put this method, which requires access to
  // package-private TagSet details.
  static void assertEqual(@Nullable TagSet a, @Nullable TagSet b) {
    if (a == null || b == null) {
      assertWithMessage("only one TagSet is null out of\n%s\n%s", a, b)
          .that(a == null && b == null)
          .isTrue();
      return;
    }
    assertThat(a.getProjectName()).isEqualTo(b.getProjectName());

    Map<String, CachedRef> aRefs = a.getRefsForTesting();
    Map<String, CachedRef> bRefs = b.getRefsForTesting();
    assertWithMessage("ref name set")
        .that(ImmutableSortedSet.copyOf(aRefs.keySet()))
        .isEqualTo(ImmutableSortedSet.copyOf(bRefs.keySet()));
    for (String name : aRefs.keySet()) {
      CachedRef aRef = aRefs.get(name);
      CachedRef bRef = bRefs.get(name);
      assertWithMessage("value of ref %s", name).that(aRef.get()).isEqualTo(bRef.get());
      assertWithMessage("flag of ref %s", name).that(aRef.flag).isEqualTo(bRef.flag);
    }

    ObjectIdOwnerMap<Tag> aTags = a.getTagsForTesting();
    ObjectIdOwnerMap<Tag> bTags = b.getTagsForTesting();
    assertWithMessage("tag ID set").that(getTagIds(aTags)).isEqualTo(getTagIds(bTags));
    for (Tag aTag : aTags) {
      Tag bTag = bTags.get(aTag);
      assertWithMessage("flags for tag %s", aTag.name())
          .that(aTag.refFlags)
          .isEqualTo(bTag.refFlags);
    }
  }

  private static ImmutableSortedSet<String> getTagIds(ObjectIdOwnerMap<Tag> bTags) {
    return Streams.stream(bTags)
        .map(Tag::name)
        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
  }
}
