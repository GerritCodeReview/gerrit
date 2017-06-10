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

package com.google.gerrit.httpd.restapi;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.httpd.restapi.ParameterParser.QueryParams;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

public class ParameterParserTest {
  @Test
  public void convertFormToJson() throws BadRequestException {
    JsonObject obj =
        ParameterParser.formToJson(
            ImmutableMap.of(
                "message", new String[] {"this.is.text"},
                "labels.Verified", new String[] {"-1"},
                "labels.Code-Review", new String[] {"2"},
                "a_list", new String[] {"a", "b"}),
            ImmutableSet.of("q"));

    JsonObject labels = new JsonObject();
    labels.addProperty("Verified", "-1");
    labels.addProperty("Code-Review", "2");
    JsonArray list = new JsonArray();
    list.add(new JsonPrimitive("a"));
    list.add(new JsonPrimitive("b"));
    JsonObject exp = new JsonObject();
    exp.addProperty("message", "this.is.text");
    exp.add("labels", labels);
    exp.add("a_list", list);

    assertEquals(exp, obj);
  }

  @Test
  public void parseQuery() throws BadRequestException {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("query=status%3aopen");
    QueryParams qp = ParameterParser.getQueryParams(req);
    assertThat(qp.accessToken()).isNull();
    assertThat(qp.xdMethod()).isNull();
    assertThat(qp.xdContentType()).isNull();
    assertThat(qp.hasXdOverride()).isFalse();
    assertThat(qp.config()).isEmpty();
    assertThat(qp.params()).containsKey("query");
    assertThat(qp.params().get("query")).containsExactly("status:open");
  }

  @Test
  public void parseAccessToken() throws BadRequestException {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("query=status%3aopen&access_token=secr%33t");
    QueryParams qp = ParameterParser.getQueryParams(req);
    assertThat(qp.accessToken()).isEqualTo("secret");
    assertThat(qp.xdMethod()).isNull();
    assertThat(qp.xdContentType()).isNull();
    assertThat(qp.hasXdOverride()).isFalse();
    assertThat(qp.config()).isEmpty();
    assertThat(qp.params()).containsKey("query");
    assertThat(qp.params().get("query")).containsExactly("status:open");

    req = new FakeHttpServletRequest();
    req.setQueryString("access_token=secret");
    qp = ParameterParser.getQueryParams(req);
    assertThat(qp.accessToken()).isEqualTo("secret");
    assertThat(qp.xdMethod()).isNull();
    assertThat(qp.xdContentType()).isNull();
    assertThat(qp.hasXdOverride()).isFalse();
    assertThat(qp.config()).isEmpty();
    assertThat(qp.params()).isEmpty();
  }

  @Test
  public void parseXdOverride() throws BadRequestException {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("$m=PUT&$ct=json&access_token=secret");
    QueryParams qp = ParameterParser.getQueryParams(req);
    assertThat(qp.accessToken()).isEqualTo("secret");
    assertThat(qp.xdMethod()).isEqualTo("PUT");
    assertThat(qp.xdContentType()).isEqualTo("json");
    assertThat(qp.hasXdOverride()).isTrue();
    assertThat(qp.config()).isEmpty();
    assertThat(qp.params()).isEmpty();
  }

  @Test
  public void rejectDuplicateMethod() {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("$m=PUT&$m=DELETE");
    try {
      ParameterParser.getQueryParams(req);
      fail("expected BadRequestException");
    } catch (BadRequestException bad) {
      assertThat(bad).hasMessageThat().isEqualTo("duplicate $m");
    }
  }

  @Test
  public void rejectDuplicateContentType() {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("$ct=json&$ct=string");
    try {
      ParameterParser.getQueryParams(req);
      fail("expected BadRequestException");
    } catch (BadRequestException bad) {
      assertThat(bad).hasMessageThat().isEqualTo("duplicate $ct");
    }
  }

  @Test
  public void rejectInvalidMethod() {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setQueryString("$m=CONNECT");
    try {
      ParameterParser.getQueryParams(req);
      fail("expected BadRequestException");
    } catch (BadRequestException bad) {
      assertThat(bad).hasMessageThat().isEqualTo("invalid $m");
    }
  }
}
