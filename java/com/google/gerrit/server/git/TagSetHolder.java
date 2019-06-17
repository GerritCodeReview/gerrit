// Copyright (C) 2011 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class TagSetHolder {
  private final Object buildLock = new Object();
  private final Project.NameKey projectName;

  @Nullable private volatile TagSet tagSet;

  TagSetHolder(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  TagSet getTagSet() {
    return tagSet;
  }

  void setTagSet(TagSet tagSet) {
    this.tagSet = tagSet;
  }

  public TagMatcher matcher(
      TagCache cache, Repository db, Collection<Ref> include, Iterable<Ref> tags)
      throws IOException {
    include = include.stream().filter(r -> !TagSet.skip(r)).collect(toList());

    TagSet tagSet = this.tagSet;
    if (tagSet == null) {
      tagSet = build(cache, db);
    }

    TagMatcher m = new TagMatcher(this, cache, db, include, tagSet, tags);
    if (!m.toUpdate.isEmpty()) {
      tagSet = rebuild(cache, db, tagSet, m);

      m = new TagMatcher(this, cache, db, include, tagSet, tags);
    }
    return m;
  }

  private TagSet build(TagCache cache, Repository db) throws IOException {
    synchronized (buildLock) {
      TagSet tagSet = this.tagSet;
      if (tagSet == null) {
        tagSet = new TagSet(projectName);
        tagSet.build(db, null, null);
        this.tagSet = tagSet;
        cache.put(projectName, this);
      }
      return tagSet;
    }
  }

  private TagSet rebuild(TagCache cache, Repository db, TagSet old, TagMatcher m)
      throws IOException {
    synchronized (buildLock) {
      TagSet cur = this.tagSet;
      if (cur == old) {
        cur = new TagSet(projectName);
        cur.build(db, old, m);
        this.tagSet = cur;
        cache.put(projectName, this);
      }
      return cur;
    }
  }

  enum Serializer implements CacheSerializer<TagSetHolder> {
    INSTANCE;

    @Override
    public byte[] serialize(TagSetHolder object) {
      TagSetHolderProto.Builder b =
          TagSetHolderProto.newBuilder().setProjectName(object.projectName.get());
      TagSet tagSet = object.tagSet;
      if (tagSet != null) {
        b.setTags(tagSet.toProto());
      }
      return Protos.toByteArray(b.build());
    }

    @Override
    public TagSetHolder deserialize(byte[] in) {
      TagSetHolderProto proto = Protos.parseUnchecked(TagSetHolderProto.parser(), in);
      TagSetHolder holder = new TagSetHolder(Project.nameKey(proto.getProjectName()));
      if (proto.hasTags()) {
        holder.tagSet = TagSet.fromProto(proto.getTags());
      }
      return holder;
    }
  }
}
