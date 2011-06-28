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

package com.google.gerrit.server.index;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

import java.util.Set;

class NoDocumentsQuery extends Query {
  private static final Term NONE = new Term("NoDocuments", "");

  @Override
  public void extractTerms(Set<Term> terms) {
    terms.add(NONE);
  }

  @Override
  public String toString(String field) {
    return NONE.field() + ":";
  }
}
