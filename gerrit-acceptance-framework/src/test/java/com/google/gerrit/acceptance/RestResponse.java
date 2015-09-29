// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.gerrit.httpd.restapi.RestApiServlet.JSON_MAGIC;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class RestResponse extends HttpResponse {

  RestResponse(org.apache.http.HttpResponse response) {
    super(response);
  }

  @Override
  public Reader getReader() throws IllegalStateException, IOException {
    if (reader == null && response.getEntity() != null) {
      reader =
          new InputStreamReader(response.getEntity().getContent(),
              StandardCharsets.UTF_8);
      reader.skip(JSON_MAGIC.length);
    }
    return reader;
  }
}
