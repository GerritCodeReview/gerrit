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
import com.google.gerrit.proto.Protos;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.util.Collection;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class TagSetHolder {
  private final Object buildLock = new Object();
  private final Project.NameKey projectName;

  @Nullable private volatile TagSet tags;

  TagSetHolder(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  TagSet getTagSet() {
    return tags;
  }

  void setTagSet(TagSet tags) {
    this.tags = tags;
  }

  public TagMatcher matcher(TagCache cache, Repository db, Collection<Ref> include) {
    include = include.stream().filter(r -> !TagSet.skip(r)).collect(toList());

    TagSet tags = this.tags;
    if (tags == null) {
      tags = build(cache, db);
    }

    TagMatcher m = new TagMatcher(this, cache, db, include, tags, false);
    tags.prepare(m);
    if (!m.newRefs.isEmpty() || !m.lostRefs.isEmpty()) {
      tags = rebuild(cache, db, tags, m);

      m = new TagMatcher(this, cache, db, include, tags, true);
      tags.prepare(m);
    }
    return m;
  }

  void rebuildForNewTags(TagCache cache, TagMatcher m) {
    m.tags = rebuild(cache, m.db, m.tags, null);
    m.mask.clear();
    m.newRefs.clear();
    m.lostRefs.clear();
    m.tags.prepare(m);
  }

  private TagSet build(TagCache cache, Repository db) {
    synchronized (buildLock) {
      TagSet tags = this.tags;
      if (tags == null) {
        tags = new TagSet(projectName);
        tags.build(db, null, null);
        this.tags = tags;
        cache.put(projectName, this);
      }
      return tags;
    }
  }

  private TagSet rebuild(TagCache cache, Repository db, TagSet old, TagMatcher m) {
    synchronized (buildLock) {
      TagSet cur = this.tags;
      if (cur == old) {
        cur = new TagSet(projectName);
        cur.build(db, old, m);
        this.tags = cur;
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
      TagSet tags = object.tags;
      if (tags != null) {
        b.setTags(tags.toProto());
      }
      return Protos.toByteArray(b.build());
    }

    @Override
    public TagSetHolder deserialize(byte[] in) {
      TagSetHolderProto proto = Protos.parseUnchecked(TagSetHolderProto.parser(), in);
      TagSetHolder holder = new TagSetHolder(Project.nameKey(proto.getProjectName()));
      if (proto.hasTags()) {
        holder.tags = TagSet.fromProto(proto.getTags());
      }
      return holder;
    }
  }
}
