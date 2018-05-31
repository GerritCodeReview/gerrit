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

package com.google.gerrit.elasticsearch.bulk;

import java.util.ArrayList;
import java.util.List;

public abstract class BulkRequest {

  private final List<BulkRequest> requests = new ArrayList<>();

  protected BulkRequest() {
    add(this);
  }

  public BulkRequest add(BulkRequest request) {
    requests.add(request);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (BulkRequest request : requests) {
      builder.append(request.getRequest());
    }
    return builder.toString();
  }

  protected abstract String getRequest();
}
