/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fakeRun1} from '../../models/checks/checks-fakes';
import {RunResult} from '../../models/checks/checks-model';
import '../../test/common-test-setup-karma';
import './gr-diff-check-result';
import {GrDiffCheckResult} from './gr-diff-check-result';

suite('gr-diff-check-result tests', () => {
  let element: GrDiffCheckResult;

  setup(async () => {
    element = document.createElement('gr-diff-check-result');
    document.body.appendChild(element);
    await element.updateComplete;
  });

  teardown(() => {
    if (element) element.remove();
  });

  test('renders', async () => {
    element.result = {...fakeRun1, ...fakeRun1.results?.[0]} as RunResult;
    await element.updateComplete;
    // cannot use /* HTML */ because formatted long message will not match.
    expect(element).shadowDom.to.equal(`
      <div class="container font-normal warning">
        <div class="header">
          <div class="icon">
            <iron-icon icon="gr-icons:warning"> </iron-icon>
          </div>
          <div class="name">
            <gr-hovercard-run> </gr-hovercard-run>
            <div class="name" role="button" tabindex="0">FAKE Super Check</div>
          </div>
          <div class="summary">We think that you could improve this.</div>
          <div class="message">
            There is a lot to be said. A lot. I say, a lot.
                So please keep reading.
          </div>
        </div>
        <div class="details"></div>
      </div>
    `);
  });
});
