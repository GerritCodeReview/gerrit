/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {fakeRun1} from '../../models/checks/checks-fakes';
import {RunResult} from '../../models/checks/checks-model';
import '../../test/common-test-setup';
import {queryAndAssert} from '../../utils/common-util';
import './gr-diff-check-result';
import {GrDiffCheckResult} from './gr-diff-check-result';
import {getAppContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';
import {GrButton} from '../shared/gr-button/gr-button';
import {suggestionsServiceToken} from '../../services/suggestions/suggestions-service';
import {testResolver} from '../../test/common-test-setup';

suite('gr-diff-check-result tests', () => {
  let element: GrDiffCheckResult;
  let flagsService: any;
  let suggestionsService: any;

  setup(async () => {
    flagsService = getAppContext().flagsService;
    suggestionsService = testResolver(suggestionsServiceToken);

    // Enable AI fix feature flag
    sinon
      .stub(flagsService, 'isEnabled')
      .callsFake(
        (id: KnownExperimentId) => id === KnownExperimentId.GET_AI_FIX
      );

    sinon
      .stub(suggestionsService, 'isGeneratedSuggestedFixEnabled')
      .returns(true);
    sinon.stub(suggestionsService, 'generateSuggestedFix').resolves({
      description: 'AI suggested fix',
      replacements: [
        {
          path: 'test/path',
          range: {
            start_line: 1,
            start_character: 1,
            end_line: 1,
            end_character: 10,
          },
          replacement: 'fixed code',
        },
      ],
    });

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
    assert.shadowDom.equal(
      element,
      `
        <div class="container font-normal warning">
          <div class="header">
            <div class="icon">
              <gr-icon icon="warning" filled></gr-icon>
            </div>
            <div class="name">
              <gr-hovercard-run> </gr-hovercard-run>
              <div class="name" role="button" tabindex="0">
                FAKE Super Check
              </div>
            </div>
            <div class="summary">We think that you could improve this.</div>
            <div class="message">
              There is a lot to be said. A lot. I say, a lot.
                So please keep reading.
            </div>
            <div
              aria-checked="false"
              aria-label="Expand result row"
              class="show-hide"
              role="switch"
              tabindex="0"
            >
              <gr-icon icon="expand_more"></gr-icon>
            </div>
          </div>
          <div class="details">
            <div class="actions">
              <gr-checks-action
                id="please-fix"
                context="diff-fix"
              ></gr-checks-action>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders expanded', async () => {
    element.result = {...fakeRun1, ...fakeRun1.results?.[2]} as RunResult;
    element.isExpanded = true;
    await element.updateComplete;

    const details = queryAndAssert(element, 'div.details');
    assert.dom.equal(
      details,
      /* HTML */ `
        <div class="details">
          <gr-result-expanded hidecodepointers=""></gr-result-expanded>
          <div class="actions">
            <gr-checks-action
              id="please-fix"
              context="diff-fix"
            ></gr-checks-action>
          </div>
        </div>
      `
    );
  });
  suite('AI fix button', () => {
    setup(async () => {
      element.result = {
        checkName: 'Test Check',
        category: 'ERROR',
        summary: 'Test Summary',
        message: 'Test Message',
        codePointers: [
          {
            path: 'test/path',
            range: {
              start_line: 1,
              start_character: 1,
              end_line: 1,
              end_character: 10,
            },
          },
        ],
      } as RunResult;

      element.isOwner = true;
      await element.updateComplete;
    });

    test('expands when suggestion is found', async () => {
      // Initially not expanded
      assert.isFalse(element.isExpanded);

      // Click the AI fix button
      const aiFixButton = queryAndAssert<GrButton>(element, '#aiFixBtn');
      aiFixButton.click();
      await element.updateComplete;

      // Should be expanded after suggestion is found
      assert.isTrue(element.isExpanded);
    });
  });
});
