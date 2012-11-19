// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.Account;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ReviewerJson {
  ReviewerJson() {
  }

  public ReviewerInfo format(ReviewerResource reviewerResource) {
    Account account = reviewerResource.getAccount();
    int id = account.getId().get();
    String email = null;
    String name = null;
    try {
      email = URLEncoder.encode(account.getPreferredEmail(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encode reviewer email", e);
    }
    name = account.getFullName();
    if (name != null) {
      try {
        name = URLEncoder.encode(account.getFullName(), "UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Cannot encode reviewer name", e);
      }
    }
    return new ReviewerInfo(id, email, name);
  }

  public static class ReviewerInfo {
    final String kind = "gerritcodereview#reviewer";
    final int id;
    final String email;
    final String name;

    public ReviewerInfo(int id, String email, String name) {
      this.id = id;
      this.email = email;
      this.name = name;
    }
  }
}
