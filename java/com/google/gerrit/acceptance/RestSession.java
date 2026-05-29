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

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.extensions.restapi.RawInput;
import org.apache.http.Header;
import org.apache.http.client.fluent.Request;

/** Makes rest requests to gerrit backend. */
@UsedAt(UsedAt.Project.GOOGLE) // Google has own implementation of this interface in tests.
public interface RestSession {
  String url();

  RestResponse execute(Request request) throws Exception;

  RestResponse get(String endPoint) throws Exception;

  RestResponse getJsonAccept(String endPoint) throws Exception;

  RestResponse getWithHeaders(String endPoint, Header... headers) throws Exception;

  RestResponse head(String endPoint) throws Exception;

  RestResponse put(String endPoint) throws Exception;

  RestResponse put(String endPoint, Object content) throws Exception;

  RestResponse putWithHeaders(String endPoint, Header... headers) throws Exception;

  RestResponse putWithHeaders(String endPoint, Object content, Header... headers) throws Exception;

  RestResponse putRaw(String endPoint, RawInput stream) throws Exception;

  RestResponse post(String endPoint) throws Exception;

  RestResponse post(String endPoint, Object content) throws Exception;

  RestResponse postWithHeaders(String endPoint, Object content, Header... headers) throws Exception;

  RestResponse delete(String endPoint) throws Exception;

  RestResponse deleteWithHeaders(String endPoint, Header... headers) throws Exception;

  String getUrl(String endPoint);
}
