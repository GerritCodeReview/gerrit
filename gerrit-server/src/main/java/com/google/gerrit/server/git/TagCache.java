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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@Singleton
public class TagCache {
  private static final String CACHE_NAME = "git_tags";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<EntryKey, EntryVal>> type =
            new TypeLiteral<Cache<EntryKey, EntryVal>>() {};
        disk(type, CACHE_NAME);
        bind(TagCache.class);
      }
    };
  }

  private final Cache<EntryKey, EntryVal> cache;

  @Inject
  TagCache(@Named(CACHE_NAME) Cache<EntryKey, EntryVal> cache) {
    this.cache = cache;
  }

  TagSetHolder get(Project.NameKey name) {
    EntryKey key = new EntryKey(name);
    EntryVal val = cache.get(key);
    if (val == null) {
      synchronized (cache) {
        val = cache.get(key);
        if (val == null) {
          val = new EntryVal();
          val.holder = new TagSetHolder(name);
          cache.put(key, val);
        }
      }
    }
    return val.holder;
  }

  static class EntryKey implements Serializable {
    static final long serialVersionUID = 1L;

    private transient String name;

    EntryKey(Project.NameKey name) {
      this.name = name.get();
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        return name.equals(((EntryKey) o).name);
      }
      return false;
    }

    private void readObject(ObjectInputStream in) throws IOException {
      name = in.readUTF();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeUTF(name);
    }
  }

  static class EntryVal implements Serializable {
    static final long serialVersionUID = EntryKey.serialVersionUID;

    transient TagSetHolder holder;

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
      holder = new TagSetHolder(new Project.NameKey(in.readUTF()));
      if (in.readBoolean()) {
        TagSet tags = new TagSet(holder.getProjectName());
        tags.readObject(in);
        holder.setTagSet(tags);
      }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      TagSet tags = holder.getTagSet();
      out.writeUTF(holder.getProjectName().get());
      out.writeBoolean(tags != null);
      if (tags != null) {
        tags.writeObject(out);
      }
    }
  }
}
