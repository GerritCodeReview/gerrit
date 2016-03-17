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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSortedMap;

public abstract class IndexDefinition<K, V, I extends Index<K, V>> {
  public interface IndexFactory<K, V, I extends Index<K, V>> {
    I create(Schema<V> schema);
  }

  private final String name;
  private final IndexCollection<K, V, I> indexCollection;
  private final IndexFactory<K, V, I> indexFactory;
  private final ImmutableSortedMap<Integer, Schema<V>> schemas;
  private final SiteIndexer<K, V, I> siteIndexer;

  protected IndexDefinition(
      String name,
      IndexCollection<K, V, I> indexCollection,
      IndexFactory<K, V, I> indexFactory,
      SiteIndexer<K, V, I> siteIndexer,
      Class<V> valueClass) {
    this.name = name;
    this.indexCollection = indexCollection;
    this.indexFactory = indexFactory;
    this.siteIndexer = siteIndexer;
    schemas = SchemaUtil.schemasFromClass(getClass(), valueClass);
  }

  public final String getName() {
    return name;
  }

  public final ImmutableSortedMap<Integer, Schema<V>> getSchemas() {
    return schemas;
  }

  public final IndexCollection<K, V, I> getIndexCollection() {
    return indexCollection;
  }

  public final IndexFactory<K, V, I> getIndexFactory() {
    return indexFactory;
  }

  public final SiteIndexer<K, V, I> getSiteIndexer() {
    return siteIndexer;
  }

  public final Schema<V> get(int version) {
    Schema<V> schema = schemas.get(version);
    checkArgument(schema != null,
        "Unrecognized %s schema version: %s", name, version);
    return schema;
  }

  public final Schema<V> getLatest() {
    return schemas.lastEntry().getValue();
  }
}
