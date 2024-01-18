// Copyright (C) 2024 The Android Open Source Project
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


import com.google.gerrit.extensions.restapi.RawInput;
import java.io.IOException;
import org.apache.http.Header;

/** Makes rest requests to backend. */
public interface RestSession {
  RestResponse get(String endPoint) throws IOException;
  RestResponse getJsonAccept(String endPoint) throws IOException;
  RestResponse getWithHeaders(String endPoint, Header... headers) throws IOException;
  RestResponse head(String endPoint) throws IOException;
  RestResponse put(String endPoint) throws IOException;
  RestResponse put(String endPoint, Object content) throws IOException;
  RestResponse putWithHeaders(String endPoint, Header... headers) throws IOException;
  RestResponse putWithHeaders(String endPoint, Object content, Header... headers) throws IOException;
  RestResponse putRaw(String endPoint, RawInput stream) throws IOException;
  RestResponse post(String endPoint) throws IOException;
  RestResponse post(String endPoint, Object content) throws IOException;
  RestResponse postWithHeaders(String endPoint, Object content, Header... headers) throws IOException;
  RestResponse delete(String endPoint) throws IOException;
  RestResponse deleteWithHeaders(String endPoint, Header... headers) throws IOException;
}
