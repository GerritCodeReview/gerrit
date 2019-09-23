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

package com.google.gerrit.acceptance.filter;

import com.google.common.base.Strings;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FakeMustInitParamsFilter implements Filter {

  // `PARAM_X` and `PARAM_Y` are init param keys, they MUST setup for FakeMustInitParamsFilter
  // otherwise this filter will be failed on init.
  private static final String INIT_PARAM_X = "PARAM_X";
  private static final String INIT_PARAM_Y = "PARAM_Y";
  // the map is used for testing
  private static final Map<String, String> initParams = new HashMap<>();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // MUST exists init params in this filter.
    String paramXValue = filterConfig.getInitParameter(INIT_PARAM_X);
    String paramYValue = filterConfig.getInitParameter(INIT_PARAM_Y);

    // this is only one of the validate scenes, check whether the value is null or empty
    if (Strings.isNullOrEmpty(paramXValue) || Strings.isNullOrEmpty(paramYValue)) {
      throw new IllegalArgumentException(
          String.format("%s init failed ,init params must be set!", this.getClass().getName()));
    }

    initParams.put(INIT_PARAM_X, paramXValue);
    initParams.put(INIT_PARAM_Y, paramYValue);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // do nothing.
  }

  // the function is used for testing
  Map<String, String> getInitParams() {
    return initParams;
  }
}
