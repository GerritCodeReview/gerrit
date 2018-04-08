// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.git;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PackParser;

public class InMemoryInserter extends ObjectInserter {
  private final ObjectReader reader;
  private final Map<ObjectId, InsertedObject> inserted = new LinkedHashMap<>();
  private final boolean closeReader;

  public InMemoryInserter(ObjectReader reader) {
    this.reader = checkNotNull(reader);
    closeReader = false;
  }

  public InMemoryInserter(Repository repo) {
    this.reader = repo.newObjectReader();
    closeReader = true;
  }

  @Override
  public ObjectId insert(int type, long length, InputStream in) throws IOException {
    return insert(InsertedObject.create(type, in));
  }

  @Override
  public ObjectId insert(int type, byte[] data) {
    return insert(type, data, 0, data.length);
  }

  @Override
  public ObjectId insert(int type, byte[] data, int off, int len) {
    return insert(InsertedObject.create(type, data, off, len));
  }

  public ObjectId insert(InsertedObject obj) {
    inserted.put(obj.id(), obj);
    return obj.id();
  }

  @Override
  public PackParser newPackParser(InputStream in) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ObjectReader newReader() {
    return new Reader();
  }

  @Override
  public void flush() {
    // Do nothing; objects are not written to the repo.
  }

  @Override
  public void close() {
    if (closeReader) {
      reader.close();
    }
  }

  public ImmutableList<InsertedObject> getInsertedObjects() {
    return ImmutableList.copyOf(inserted.values());
  }

  public int getInsertedObjectCount() {
    return inserted.values().size();
  }

  public void clear() {
    inserted.clear();
  }

  private class Reader extends ObjectReader {
    @Override
    public ObjectReader newReader() {
      return new Reader();
    }

    @Override
    public Collection<ObjectId> resolve(AbbreviatedObjectId id) throws IOException {
      Set<ObjectId> result = new HashSet<>();
      for (ObjectId insId : inserted.keySet()) {
        if (id.prefixCompare(insId) == 0) {
          result.add(insId);
        }
      }
      result.addAll(reader.resolve(id));
      return result;
    }

    @Override
    public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
      InsertedObject obj = inserted.get(objectId);
      if (obj == null) {
        return reader.open(objectId, typeHint);
      }
      if (typeHint != OBJ_ANY && obj.type() != typeHint) {
        throw new IncorrectObjectTypeException(objectId.copy(), typeHint);
      }
      return obj.newLoader();
    }

    @Override
    public Set<ObjectId> getShallowCommits() throws IOException {
      return reader.getShallowCommits();
    }

    @Override
    public void close() {
      // Do nothing; this class owns no open resources.
    }

    @Override
    public ObjectInserter getCreatedFromInserter() {
      return InMemoryInserter.this;
    }
  }
}
