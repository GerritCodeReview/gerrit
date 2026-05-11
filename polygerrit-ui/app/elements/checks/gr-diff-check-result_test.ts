/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, waitUntil} from '@open-wc/testing';
import {checkRun1} from '../../test/test-data-generators';
import {RunResult} from '../../models/checks/checks-model';
import '../../test/common-test-setup';
import {queryAndAssert} from '../../utils/common-util';
import {stubFlags} from '../../test/test-utils';
import './gr-diff-check-result';
import {GrDiffCheckResult} from './gr-diff-check-result';
import {GrButton} from '../shared/gr-button/gr-button';
import {
  SuggestionsService,
  suggestionsServiceToken,
} from '../../services/suggestions/suggestions-service';
import {testResolver} from '../../test/common-test-setup';
import {checksModelToken} from '../../models/checks/checks-model';
import {Interaction} from '../../constants/reporting';
import {getAppContext} from '../../services/app-context';
import {FixId, NumericChangeId} from '../../api/rest-api';

suite('gr-diff-check-result tests', () => {
  let element: GrDiffCheckResult;

  let reportingStub: sinon.SinonStub;
  let suggestionsService: SuggestionsService;

  setup(async () => {
    suggestionsService = testResolver(suggestionsServiceToken);
    reportingStub = sinon.stub(
      getAppContext().reportingService,
      'reportInteraction'
    );
    const checksModel = testResolver(checksModelToken);
    checksModel.changeNum = 123 as NumericChangeId;

    sinon
      .stub(suggestionsService, 'isGeneratedSuggestedFixEnabled')
      .returns(true);
    stubFlags('isEnabled').returns(true);
    sinon.stub(suggestionsService, 'generateSuggestedFix').resolves({
      description: 'AI suggested fix',
      fix_id: '1' as FixId,
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
    element.result = {...checkRun1, ...checkRun1.results?.[0]} as RunResult;
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
            <div class="ai-icon-wrapper">
              <gr-icon custom="" icon="ai" small></gr-icon>
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
          </div>
          <div class="footer">
            <div class="tags">
              <gr-checks-tag></gr-checks-tag>
              <gr-checks-tag></gr-checks-tag>
            </div>
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
    element.result = {...checkRun1, ...checkRun1.results?.[2]} as RunResult;
    element.isExpanded = true;
    await element.updateComplete;

    const details = queryAndAssert(element, 'div.details');
    assert.dom.equal(
      details,
      /* HTML */ `
        <div class="details">
          <gr-result-expanded hidecodepointers=""></gr-result-expanded>
        </div>
      `
    );
  });

  test('shows please-fix button for author', async () => {
    element.result = {...checkRun1, ...checkRun1.results?.[0]} as RunResult;
    element.isOwner = true;
    await element.updateComplete;
    const button = queryAndAssert(element, '#please-fix');
    assert.isOk(button);
  });

  suite('AI fix button', () => {
    setup(async () => {
      element.result = {
        checkName: 'Test Check',
        category: 'ERROR',
        summary: 'Test Summary',
        message: 'Test Message',
        externalId: JSON.stringify({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 1,
        }),
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
      await waitUntil(() => element.isExpanded);

      assert.isTrue(
        reportingStub.calledWith(
          Interaction.AI_AGENT_GET_FIX_CLICKED,
          sinon.match({
            agentId: 'test-agent',
            conversationId: 'test-conv',
            turnIndex: 1,
          })
        )
      );
    });
  });
  test('reports AI_AGENT_SUGGESTION_TO_COMMENT when please-fix clicked', async () => {
    element.result = {
      ...checkRun1,
      ...checkRun1.results?.[0],
      externalId: JSON.stringify({
        agentId: 'test-agent',
        conversationId: 'test-conv',
        turnIndex: 1,
      }),
    } as RunResult;
    element.isOwner = true;
    await element.updateComplete;

    const pleaseFixButton = queryAndAssert(element, '#please-fix');
    const button = queryAndAssert<GrButton>(pleaseFixButton, 'gr-button');
    button.click();

    assert.isTrue(
      reportingStub.calledWith(Interaction.AI_AGENT_SUGGESTION_TO_COMMENT)
    );
  });

  test('reports AI_AGENT_SUGGESTION_CONTENT_COPIED when container is copied', async () => {
    element.result = {
      ...checkRun1,
      ...checkRun1.results?.[0],
      externalId: JSON.stringify({
        agentId: 'test-agent',
        conversationId: 'test-conv',
        turnIndex: 1,
      }),
    } as RunResult;
    element.isOwner = true;
    await element.updateComplete;

    const container = queryAndAssert(element, '.container');
    container.dispatchEvent(new Event('copy'));

    assert.isTrue(
      reportingStub.calledWith(
        Interaction.AI_AGENT_SUGGESTION_CONTENT_COPIED,
        sinon.match({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 1,
        })
      )
    );
  });
});
