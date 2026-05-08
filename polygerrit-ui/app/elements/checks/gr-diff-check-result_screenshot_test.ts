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
import {testResolver} from '../../test/common-test-setup';
import {changeModelToken} from '../../models/change/change-model';
import {checksModelToken} from '../../models/checks/checks-model';
import {suggestionsServiceToken} from '../../services/suggestions/suggestions-service';
import {GrDiffCheckResult} from './gr-diff-check-result';
import './gr-diff-check-result';
import {checkRun1} from '../../test/test-data-generators';
import {RunResult} from '../../models/checks/checks-model';

suite('gr-diff-check-result screenshot tests', () => {
  let element: GrDiffCheckResult;

  setup(async () => {
    testResolver(changeModelToken);
    testResolver(checksModelToken);
    const suggestionsService = testResolver(suggestionsServiceToken);
    sinon
      .stub(suggestionsService, 'isGeneratedSuggestedFixEnabled')
      .returns(true);

    element = await fixture(
      html`<gr-diff-check-result></gr-diff-check-result>`
    );
    element.style.display = 'block';
    element.style.width = '600px';
    await element.updateComplete;
  });

  test('collapsed', async () => {
    element.result = {...checkRun1, ...checkRun1.results?.[0]} as RunResult;
    element.result.isAiPowered = false;
    await element.updateComplete;
    await document.fonts.ready;
    await new Promise(resolve => setTimeout(resolve, 500));

    await visualDiff(element, 'gr-diff-check-result-collapsed');
    await visualDiffDarkTheme(element, 'gr-diff-check-result-collapsed');
  });

  test('expanded', async () => {
    element.result = {...checkRun1, ...checkRun1.results?.[2]} as RunResult;
    element.isExpanded = true;
    element.result.isAiPowered = false;
    await element.updateComplete;
    await document.fonts.ready;
    await new Promise(resolve => setTimeout(resolve, 500));

    await visualDiff(element, 'gr-diff-check-result-expanded');
    await visualDiffDarkTheme(element, 'gr-diff-check-result-expanded');
  });

  test('collapsed with AI powered check results', async () => {
    element.result = {...checkRun1, ...checkRun1.results?.[0]} as RunResult;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-check-result-collapsed-ai-powered');
    await visualDiffDarkTheme(
      element,
      'gr-diff-check-result-collapsed-ai-powered'
    );
  });

  test('expanded with AI powered check results', async () => {
    element.result = {...checkRun1, ...checkRun1.results?.[2]} as RunResult;
    element.isExpanded = true;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-check-result-expanded-ai-powered');
    await visualDiffDarkTheme(
      element,
      'gr-diff-check-result-expanded-ai-powered'
    );
  });

  test('with unpublished tag', async () => {
    element.result = {
      ...checkRun1,
      ...checkRun1.results?.[0],
      tags: [{name: 'Unpublished', tooltip: 'This is unpublished'}],
    } as RunResult;
    await element.updateComplete;
    await visualDiff(element, 'gr-diff-check-result-unpublished');
    await visualDiffDarkTheme(element, 'gr-diff-check-result-unpublished');
  });
});
