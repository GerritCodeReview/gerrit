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

package com.google.gerrit.lucene;

import static com.google.gerrit.server.index.change.ChangeSchemaDefinitions.NAME;

import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class ChangeSubIndex extends AbstractLuceneIndex<Change.Id, ChangeData>
    implements ChangeIndex {
  ChangeSubIndex(
      Schema<ChangeData> schema,
      SitePaths sitePaths,
      Path path,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory)
      throws IOException {
    this(
        schema,
        sitePaths,
        FSDirectory.open(path),
        path.getFileName().toString(),
        writerConfig,
        searcherFactory);
  }

  ChangeSubIndex(
      Schema<ChangeData> schema,
      SitePaths sitePaths,
      Directory dir,
      String subIndex,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory)
      throws IOException {
    super(schema, sitePaths, dir, NAME, subIndex, writerConfig, searcherFactory);
  }

  @Override
  public void replace(ChangeData obj) throws IOException {
    throw new UnsupportedOperationException("don't use ChangeSubIndex directly");
  }

  @Override
  public void delete(Change.Id key) throws IOException {
    throw new UnsupportedOperationException("don't use ChangeSubIndex directly");
  }

  @Override
  public DataSource<ChangeData> getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    throw new UnsupportedOperationException("don't use ChangeSubIndex directly");
  }

  // Make method public so that it can be used in LuceneChangeIndex
  @Override
  public FieldBundle toFieldBundle(Document doc) {
    return super.toFieldBundle(doc);
  }

  @Override
  protected ChangeData fromDocument(Document doc) {
    throw new UnsupportedOperationException("don't use ChangeSubIndex directly");
  }
}
