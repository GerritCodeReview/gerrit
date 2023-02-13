// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.httpd.raw.IndexPreloadingUtil.computeChangeRequestsPath;
import static com.google.gerrit.httpd.raw.IndexPreloadingUtil.parseRequestedPage;

import com.google.gerrit.httpd.raw.IndexPreloadingUtil.RequestedPage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IndexPreloadingUtilTest {

  @Test
  public void computeChangePath() throws Exception {
    assertThat(computeChangeRequestsPath("/c/project/+/123", RequestedPage.CHANGE))
        .hasValue("changes/project~123");

    assertThat(computeChangeRequestsPath("/c/project/+/124/2", RequestedPage.CHANGE))
        .hasValue("changes/project~124");

    assertThat(computeChangeRequestsPath("/c/project/src/+/23", RequestedPage.CHANGE))
        .hasValue("changes/project%2Fsrc~23");

    assertThat(computeChangeRequestsPath("/q/project/src/+/23", RequestedPage.CHANGE).isPresent())
        .isFalse();

    assertThat(
            computeChangeRequestsPath("/c/Scripts/+/232/1//COMMIT_MSG", RequestedPage.CHANGE)
                .isPresent())
        .isFalse();
    assertThat(computeChangeRequestsPath("/c/Scripts/+/232/1//COMMIT_MSG", RequestedPage.DIFF))
        .hasValue("changes/Scripts~232");
  }

  @Test
  public void preloadOnlyForSelfDashboard() throws Exception {
    assertThat(parseRequestedPage("/dashboard/self")).isEqualTo(RequestedPage.DASHBOARD);
    assertThat(parseRequestedPage("/profile/self")).isEqualTo(RequestedPage.PROFILE);
    assertThat(parseRequestedPage("/dashboard/1085901"))
        .isEqualTo(RequestedPage.PAGE_WITHOUT_PRELOADING);
    assertThat(parseRequestedPage("/dashboard/gerrit"))
        .isEqualTo(RequestedPage.PAGE_WITHOUT_PRELOADING);
    assertThat(parseRequestedPage("/")).isEqualTo(RequestedPage.DASHBOARD);
  }
}
