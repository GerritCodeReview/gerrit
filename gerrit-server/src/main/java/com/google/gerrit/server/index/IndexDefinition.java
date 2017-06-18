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

import com.google.common.collect.ImmutableSortedMap;

/**
 * Definition of an index over a Gerrit data type.
 *
 * <p>An <em>index</em> includes a set of schema definitions along with the specific implementations
 * used to query the secondary index implementation in a running server. If you are just interested
 * in the static definition of one or more schemas, see the implementations of {@link
 * SchemaDefinitions}.
 */
public abstract class IndexDefinition<K, V, I extends Index<K, V>> {
  public interface IndexFactory<K, V, I extends Index<K, V>> {
    I create(Schema<V> schema);
  }

  private final SchemaDefinitions<V> schemaDefs;
  private final IndexCollection<K, V, I> indexCollection;
  private final IndexFactory<K, V, I> indexFactory;
  private final SiteIndexer<K, V, I> siteIndexer;

  protected IndexDefinition(
      SchemaDefinitions<V> schemaDefs,
      IndexCollection<K, V, I> indexCollection,
      IndexFactory<K, V, I> indexFactory,
      SiteIndexer<K, V, I> siteIndexer) {
    this.schemaDefs = schemaDefs;
    this.indexCollection = indexCollection;
    this.indexFactory = indexFactory;
    this.siteIndexer = siteIndexer;
  }

  public final String getName() {
    return schemaDefs.getName();
  }

  public final ImmutableSortedMap<Integer, Schema<V>> getSchemas() {
    return schemaDefs.getSchemas();
  }

  public final Schema<V> getLatest() {
    return schemaDefs.getLatest();
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
}
