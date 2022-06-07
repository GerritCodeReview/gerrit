/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
