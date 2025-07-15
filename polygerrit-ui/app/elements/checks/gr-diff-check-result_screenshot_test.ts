/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../test/test-utils';
import {GrDiffCheckResult} from './gr-diff-check-result';
import './gr-diff-check-result';
import {fakeRun1} from '../../models/checks/checks-fakes';
import {RunResult} from '../../models/checks/checks-model';

suite('gr-diff-check-result screenshot tests', () => {
  let element: GrDiffCheckResult;

  setup(async () => {
    element = await fixture(
      html`<gr-diff-check-result></gr-diff-check-result>`
    );
  });

  test('collapsed', async () => {
    element.result = {...fakeRun1, ...fakeRun1.results?.[0]} as RunResult;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-check-result-collapsed');
    await visualDiffDarkTheme(element, 'gr-diff-check-result-collapsed');
  });

  test('expanded', async () => {
    element.result = {...fakeRun1, ...fakeRun1.results?.[2]} as RunResult;
    element.isExpanded = true;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-check-result-expanded');
    await visualDiffDarkTheme(element, 'gr-diff-check-result-expanded');
  });
});
