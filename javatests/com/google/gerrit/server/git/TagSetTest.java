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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.roaringbitmap.RoaringBitmap;

public class TagSetTest {
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
  public void tagOnCommitsAddedByAFastForwardBecomesReachable() throws Exception {
    InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("project"));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      tr.update("refs/heads/master", root);
      RevCommit tagged = tr.commit().parent(root).create();
      tr.update("refs/tags/v1", tr.tag("v1", tagged));
      Ref tagRef = repo.exactRef("refs/tags/v1");
      TagMatcher m = tagCache.get(project).matcher(tagCache, repo, refs(repo, "refs/heads/master"));
      assertThat(m.isReachable(tagRef)).isFalse();

      tr.update("refs/heads/master", tr.commit().parent(tagged).create());
      m = tagCache.get(project).matcher(tagCache, repo, refs(repo, "refs/heads/master"));
      assertThat(m.isReachable(tagRef)).isTrue();
    }
  }

  @Test
  public void tagOnCommitsAddedByAForcePushBecomesReachable() throws Exception {
    InMemoryRepository repo = new InMemoryRepository(new DfsRepositoryDescription("project"));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      tr.update("refs/heads/master", tr.commit().parent(root).create());
      RevCommit tagged = tr.commit().parent(root).create();
      tr.update("refs/tags/v1", tr.tag("v1", tagged));
      Ref tagRef = repo.exactRef("refs/tags/v1");
      TagMatcher m = tagCache.get(project).matcher(tagCache, repo, refs(repo, "refs/heads/master"));
      assertThat(m.isReachable(tagRef)).isFalse();

      // Not a fast-forward: the old tip of master is not an ancestor of the new one.
      tr.update("refs/heads/master", tr.commit().parent(tagged).create());
      m = tagCache.get(project).matcher(tagCache, repo, refs(repo, "refs/heads/master"));
      assertThat(m.isReachable(tagRef)).isTrue();
    }
  }

  private static ImmutableList<Ref> refs(Repository repo, String... names) throws IOException {
    ImmutableList.Builder<Ref> refs = ImmutableList.builder();
    for (String name : names) {
      refs.add(repo.exactRef(name));
    }
    return refs.build();
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
