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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class FakeMustInitParamsFilter implements Filter {

  // `PARAM_X` and `PARAM_Y` are init param keys
  private static final String INIT_PARAM_1 = "PARAM-1";
  private static final String INIT_PARAM_2 = "PARAM-2";
  // the map is used for testing
  private static final Map<String, String> initParams = new HashMap<>();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    initParams.put(INIT_PARAM_1, filterConfig.getInitParameter(INIT_PARAM_1));
    initParams.put(INIT_PARAM_2, filterConfig.getInitParameter(INIT_PARAM_2));
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
