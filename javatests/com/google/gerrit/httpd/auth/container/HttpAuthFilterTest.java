// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.container;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class HttpAuthFilterTest {

  private static String DISPLAYNAME_HEADER = "displaynameHeader";
  private static String DISPLAYNAME = "displayname";

  @Mock private DynamicItem<WebSession> webSession;
  @Mock private ExternalIdKeyFactory externalIdKeyFactory;
  @Mock private AuthConfig authConfig;

  @Test
  public void getRemoteDisplaynameShouldReturnDisplaynameHeaderWhenHeaderIsConfiguredAndSet()
      throws IOException {
    doReturn(DISPLAYNAME_HEADER).when(authConfig).getHttpDisplaynameHeader();
    HttpAuthFilter httpAuthFilter =
        new HttpAuthFilter(webSession, authConfig, externalIdKeyFactory);

    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.addHeader(DISPLAYNAME_HEADER, DISPLAYNAME);

    assertThat(httpAuthFilter.getRemoteDisplayname(req)).isEqualTo(DISPLAYNAME);
  }

  @Test
  public void getRemoteDisplaynameShouldReturnNullWhenDisplaynameHeaderIsConfiguredAndNotSet()
      throws IOException {
    doReturn(DISPLAYNAME_HEADER).when(authConfig).getHttpDisplaynameHeader();
    HttpAuthFilter httpAuthFilter =
        new HttpAuthFilter(webSession, authConfig, externalIdKeyFactory);

    FakeHttpServletRequest req = new FakeHttpServletRequest();

    assertThat(httpAuthFilter.getRemoteDisplayname(req)).isNull();
  }
}
