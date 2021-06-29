/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import './common-test-setup-karma.js';

// Run a11y audit on test fixture
// The code is inspired by the
// https://github.com/Polymer/web-component-tester/blob/master/data/a11ySuite.js
export async function runA11yAudit(fixture, ignoredRules) {
  fixture.instantiate();
  await flush();
  const axsConfig = new axs.AuditConfiguration();
  axsConfig.scope = document.body;
  axsConfig.showUnsupportedRulesWarning = false;
  axsConfig.auditRulesToIgnore = ignoredRules;

  const auditResults = axs.Audit.run(axsConfig);
  const errors = [];
  auditResults.forEach((result, index) => {
    // only show applicable tests
    if (result.result === 'FAIL') {
      const title = result.rule.heading;
      // fail test if audit result is FAIL
      const error = axs.Audit.accessibilityErrorMessage(result);
      errors.push(`${title}: ${error}`);
    }
  });
  if (errors.length > 0) {
    assert.fail(errors.join('\n') + '\n');
  }
}
