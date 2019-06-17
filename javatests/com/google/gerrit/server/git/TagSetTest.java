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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.NodeProto;
import com.google.gerrit.server.git.DecoratedDag.Node;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.junit.Test;

public class TagSetTest {
  @Test
  public void roundTripToProto() {
    DecoratedDag dag = new DecoratedDag();

    Node ic1 = new Node(ObjectId.fromString("0000000000000000000000000000000000000001"));
    dag.setInitialCommit(ic1);

    String branch1 = "refs/heads/branch1";
    Node ic2 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000002"), decorations(branch1));
    dag.setInitialCommit(ic2);

    String tag1 = "refs/tags/tag1";
    Node ic3 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000003"), decorations(tag1));
    dag.setInitialCommit(ic3);

    String tag2 = "refs/tags/tag2";
    String branch2 = "refs/heads/branch2";
    Node ic4 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000004"),
            decorations(tag2, branch2));
    dag.setInitialCommit(ic4);

    String tag3 = "refs/tags/tag3";
    ObjectId idTag3 = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3");
    dag.decorate(idTag3, tag3);
    Node nodeTag3 = dag.getOrCreate(idTag3);
    nodeTag3.parents.add(ic4);

    ObjectId idMaster = ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    String master = "refs/heads/master";
    dag.decorate(idMaster, master);
    Node nodeMaster = dag.getOrCreate(idMaster);
    nodeMaster.parents.add(nodeTag3);

    String tag4 = "refs/tags/tag4";
    ObjectId idTag4 = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa4");
    dag.decorate(idTag4, tag4);
    Node nodeTag4 = dag.getOrCreate(idTag4);
    nodeTag4.parents.add(nodeTag3);

    String branch3 = "refs/heads/branch3";
    ObjectId idBranch3 = ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb3");
    dag.decorate(idBranch3, branch3);
    Node nodeBranch3 = dag.getOrCreate(idBranch3);
    nodeBranch3.parents.add(nodeTag4);

    TagSet tagSet = new TagSet(Project.nameKey("project"), dag);

    TagSetProto proto = tagSet.toProto();
    assertThat(proto)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            TagSetProto.newBuilder()
                .setProjectName("project")
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
                        .addAllDecorations(decorations(""))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02))
                        .addAllDecorations(decorations("", branch1))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03))
                        .addAllDecorations(decorations("", tag1))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04))
                        .addAllDecorations(decorations("", tag2, branch2))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xa3))
                        .addAllDecorations(decorations(tag3))
                        .addAllParentNumbers(parentNumbers(3))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb))
                        .addAllDecorations(decorations(master))
                        .addAllParentNumbers(parentNumbers(4))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xa4))
                        .addAllDecorations(decorations(tag4))
                        .addAllParentNumbers(parentNumbers(4))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xb3))
                        .addAllDecorations(decorations(branch3))
                        .addAllParentNumbers(parentNumbers(6))
                        .build())
                .build());

    assertEqual(tagSet, TagSet.fromProto(proto));
  }

  private Set<String> decorations(String... refs) {
    Set<String> decorations = new HashSet<>();
    for (String ref : refs) {
      decorations.add(ref);
    }
    return decorations;
  }

  private List<Integer> parentNumbers(Integer... parents) {
    List<Integer> parentNums = new ArrayList<>();
    for (Integer parent : parents) {
      parentNums.add(parent);
    }
    return parentNums;
  }

  @Test
  public void tagSetFields() {
    assertThatSerializedClass(TagSet.class)
        .hasFields(
            ImmutableMap.of(
                "projectName", Project.NameKey.class,
                "dag", DecoratedDag.class));
  }

  @Test
  public <V> void dagFields() {
    assertThatSerializedClass(DecoratedDag.class)
        .extendsClass(new TypeLiteral<ObjectIdOwnerMap<Node>>() {}.getType());

    assertThatSerializedClass(DecoratedDag.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("decorated", new TypeLiteral<Set<Node>>() {}.getType())
                .put("directory", new TypeLiteral<Node[][]>() {}.getType())
                .put("size", int.class)
                .put("grow", int.class)
                .put("bits", int.class)
                .put("mask", int.class)
                .build());
  }

  @Test
  public void nodeFields() {
    assertThatSerializedClass(Node.class).extendsClass(ObjectIdOwnerMap.Entry.class);
    assertThatSerializedClass(Node.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("decorations", new TypeLiteral<Set<String>>() {}.getType())
                .put("parents", new TypeLiteral<Set<Node>>() {}.getType())
                .put("next", ObjectIdOwnerMap.Entry.class)
                .put("w1", int.class)
                .put("w2", int.class)
                .put("w3", int.class)
                .put("w4", int.class)
                .put("w5", int.class)
                .build());
  }

  static void assertEqual(@Nullable TagSet a, @Nullable TagSet b) {
    if (a == null || b == null) {
      assertWithMessage("only one TagSet is null out of\n%s\n%s", a, b)
          .that(a == null && b == null)
          .isTrue();
      return;
    }
    assertThat(a.getProjectName()).isEqualTo(b.getProjectName());
    assertThat(a.dag.decorated).containsExactlyElementsIn(b.dag.decorated);
  }
}
