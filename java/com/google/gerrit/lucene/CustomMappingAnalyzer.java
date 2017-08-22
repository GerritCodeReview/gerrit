// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.lucene;

import java.io.Reader;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;

/**
 * This analyzer can be used to provide custom char mappings.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">{@code
 * Map<String,String> customMapping = new HashMap<>();
 * customMapping.put("_", " ");
 * customMapping.put(".", " ");
 *
 * CustomMappingAnalyzer analyzer =
 *   new CustomMappingAnalyzer(new StandardAnalyzer(version), customMapping);
 * }</pre>
 */
public class CustomMappingAnalyzer extends AnalyzerWrapper {
  private Analyzer delegate;
  private Map<String, String> customMappings;

  public CustomMappingAnalyzer(Analyzer delegate, Map<String, String> customMappings) {
    super(delegate.getReuseStrategy());
    this.delegate = delegate;
    this.customMappings = customMappings;
  }

  @Override
  protected Analyzer getWrappedAnalyzer(String fieldName) {
    return delegate;
  }

  @Override
  protected Reader wrapReader(String fieldName, Reader reader) {
    NormalizeCharMap.Builder builder = new NormalizeCharMap.Builder();
    for (Map.Entry<String, String> e : customMappings.entrySet()) {
      builder.add(e.getKey(), e.getValue());
    }
    return new MappingCharFilter(builder.build(), reader);
  }
}
