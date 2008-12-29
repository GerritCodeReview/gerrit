// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

import java.util.ArrayList;
import java.util.List;

public class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  public SuggestServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void suggestProjectNameKey(final String query, final int limit,
      final AsyncCallback<List<Project.NameKey>> callback) {
    run(callback, new Action<List<Project.NameKey>>() {
      public List<Project.NameKey> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + "\uffff";
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);

        final List<Project.NameKey> r = new ArrayList<Project.NameKey>();
        for (final Project p : db.projects().suggestByName(a, b, n)) {
          r.add(p.getNameKey());
        }
        return r;
      }
    });
  }
}
