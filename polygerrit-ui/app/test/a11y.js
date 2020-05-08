/**
 * @license
 * Copyright (c) 2015 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
 */

/**
 * @fileOverview This file contains a code to run a11y audit on a test fixture
 * The code is inspired by the
 * https://github.com/Polymer/web-component-tester/blob/master/data/a11ySuite.js
 */
// Run a11y audit on test fixture
// The code is
export function runA11yAudit(fixture, ignoredRules) {
  return new Promise((resolve) => {
    const element = fixture.instantiate();
    flush(() => {
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
      resolve();
    });
  });
}
