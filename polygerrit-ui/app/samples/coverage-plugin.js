/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
function populateWithDummyData(coverageData) {
  coverageData['/COMMIT_MSG'] = {
    linesMissingCoverage: [3, 4, 7, 14],
    totalLines: 14,
    changeNum: 94,
    patchNum: 2,
  };

  // more coverage info on other files
}

/**
 * This plugin will add a toggler on file diff page to
 * display fake coverage data.
 *
 * As the fake coverage data only provided for COMMIT_MSG file,
 * so it will only work for COMMIT_MSG file diff.
 */
Gerrit.install(plugin => {
  const coverageData = {};
  let displayCoverage = false;
  const annotationApi = plugin.annotationApi();
  const styleApi = plugin.styles();

  const coverageStyle = styleApi.css('background-color: #EF9B9B !important');
  const emptyStyle = styleApi.css('');

  annotationApi.setLayer(context => {
    if (Object.keys(coverageData).length === 0) {
      // Coverage data is not ready yet.
      return;
    }
    const path = context.path;
    const line = context.line;
    // Highlight lines missing coverage with this background color if
    // coverage should be displayed, else do nothing.
    const annotationStyle = displayCoverage
      ? coverageStyle
      : emptyStyle;

    // ideally should check to make sure its the same patch for same change
    // for demo purpose, this is only checking to make sure we have fake data
    if (coverageData[path]) {
      const linesMissingCoverage = coverageData[path].linesMissingCoverage;
      if (linesMissingCoverage.includes(line.afterNumber)) {
        context.annotateRange(0, line.text.length, annotationStyle, 'right');
        context.annotateLineNumber(annotationStyle, 'right');
      }
    }
  }).enableToggleCheckbox('Display Coverage', checkbox => {
    populateWithDummyData(coverageData);
    checkbox.disabled = false;
    checkbox.onclick = e => {
      displayCoverage = e.target.checked;
      Object.keys(coverageData).forEach(file => {
        annotationApi.notify(file, 0, coverageData[file].totalLines, 'right');
      });
    };
  });
});
