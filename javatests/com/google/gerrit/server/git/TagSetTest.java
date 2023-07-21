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
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
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
            ImmutableMap.of(
                "flag", int.class, "value", AtomicReference.class.getTypeParameters()[0]));
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
