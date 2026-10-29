/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, waitUntil} from '@open-wc/testing';
import {checkRun1, createComment} from '../../test/test-data-generators';
import {RunResult} from '../../models/checks/checks-model';
import {
  pleaseFixMessage,
  RESPONSE_PLACEHOLDER,
} from '../../models/checks/checks-util';
import '../../test/common-test-setup';
import {query, queryAndAssert} from '../../utils/common-util';
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
import {
  commentsModelToken,
  setComments,
  setPortedComments,
} from '../../models/comments/comments-model';
import {SavingState} from '../../types/common';
import {Interaction} from '../../constants/reporting';
import {getAppContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';
import {
  FixId,
  NumericChangeId,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../api/rest-api';
import {Category} from '../../api/checks';

suite('gr-diff-check-result tests', () => {
  let element: GrDiffCheckResult;

  let reportingStub: sinon.SinonStub;
  let isEnabledStub: sinon.SinonStub;
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
    isEnabledStub = stubFlags('isEnabled');
    isEnabledStub
      .withArgs(KnownExperimentId.ML_SUGGESTED_EDIT_GET_FIX)
      .returns(true);
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
        isAiPowered: true,
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

    const commentsModel = testResolver(commentsModelToken);
    sinon.stub(commentsModel, 'saveDraft').resolves({
      id: 'test-please-fix-draft-id' as UrlEncodedCommentId,
      savingState: SavingState.OK,
    });

    const pleaseFixButton = queryAndAssert(element, '#please-fix');
    const button = queryAndAssert<GrButton>(pleaseFixButton, 'gr-button');
    button.click();

    await new Promise(resolve => setTimeout(resolve, 0));

    assert.isTrue(
      reportingStub.calledWith(
        Interaction.AI_AGENT_SUGGESTION_TO_COMMENT,
        sinon.match({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 1,
          commentId: 'test-please-fix-draft-id',
        })
      )
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

  suite('Respond button', () => {
    test('does not show respond button when experiment is disabled', async () => {
      element.result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        category: Category.WARNING,
      } as RunResult;
      await element.updateComplete;
      assert.isUndefined(query(element, '#respond'));
    });

    test('shows respond button for WARNING when experiment is enabled', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      element.result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        category: Category.WARNING,
      } as RunResult;
      await element.updateComplete;
      assert.isDefined(query(element, '#respond'));
    });

    test('does not show respond button for ERROR even when experiment is enabled', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      element.result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        category: Category.ERROR,
      } as RunResult;
      await element.updateComplete;
      assert.isUndefined(query(element, '#respond'));
    });

    test('adds new draft comment and reports when respond button is clicked', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      element.result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        category: Category.WARNING,
        codePointers: [
          {
            path: 'test/path.ts',
            range: {
              start_line: 10,
              start_character: 0,
              end_line: 12,
              end_character: 5,
            },
          },
        ],
        externalId: JSON.stringify({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 1,
        }),
      } as RunResult;
      await element.updateComplete;

      const commentsModel = testResolver(commentsModelToken);
      const addNewDraftStub = sinon.stub(commentsModel, 'addNewDraft');

      const respondAction = queryAndAssert(element, '#respond');
      const button = queryAndAssert<GrButton>(respondAction, 'gr-button');
      button.click();

      await new Promise(resolve => setTimeout(resolve, 0));

      assert.isTrue(addNewDraftStub.calledOnce);
      const draftArg = addNewDraftStub.lastCall.args[0];
      assert.equal(draftArg.path, 'test/path.ts');
      assert.equal(draftArg.line, 12);
      assert.isTrue(draftArg.unresolved);
      assert.include(
        draftArg.message,
        `${RESPONSE_PLACEHOLDER}\n\nIn response to WARNING reported by FAKE Super Check:`
      );

      assert.isTrue(
        reportingStub.calledWith(
          Interaction.AI_AGENT_SUGGESTION_TO_COMMENT,
          sinon.match({
            agentId: 'test-agent',
            conversationId: 'test-conv',
            turnIndex: 1,
            commentId: undefined,
          })
        )
      );
    });

    test('hides please-fix and respond buttons when unresolved matching comment thread exists at location', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      const range = {
        start_line: 10,
        start_character: 0,
        end_line: 12,
        end_character: 5,
      };
      const result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        patchset: 1,
        category: Category.WARNING,
        codePointers: [{path: 'test/path.ts', range}],
      } as RunResult;
      element.result = result;
      await element.updateComplete;

      assert.isDefined(query(element, '#please-fix'));
      assert.isDefined(query(element, '#respond'));

      const commentsModel = testResolver(commentsModelToken);
      commentsModel.modifyState(s =>
        setComments(s, {
          'test/path.ts': [
            createComment({
              id: 'c1' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              line: 12,
              range,
              unresolved: true,
              message: pleaseFixMessage(result),
            }),
          ],
        })
      );
      await element.updateComplete;

      assert.isUndefined(query(element, '#please-fix'));
      assert.isUndefined(query(element, '#respond'));
    });

    test('keeps buttons when matching comment thread is resolved', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      const range = {
        start_line: 10,
        start_character: 0,
        end_line: 12,
        end_character: 5,
      };
      const result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        patchset: 1,
        category: Category.WARNING,
        codePointers: [{path: 'test/path.ts', range}],
      } as RunResult;
      element.result = result;

      const commentsModel = testResolver(commentsModelToken);
      commentsModel.modifyState(s =>
        setComments(s, {
          'test/path.ts': [
            createComment({
              id: 'c1' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              line: 12,
              range,
              unresolved: false,
              message: pleaseFixMessage(result),
            }),
          ],
        })
      );
      await element.updateComplete;

      assert.isDefined(query(element, '#please-fix'));
      assert.isDefined(query(element, '#respond'));
    });

    test('hides buttons when unresolved thread from earlier patchset is ported to moved location', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      const originalRange = {
        start_line: 10,
        start_character: 0,
        end_line: 12,
        end_character: 5,
      };
      const movedRange = {
        start_line: 15,
        start_character: 0,
        end_line: 17,
        end_character: 5,
      };
      const result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        patchset: 2,
        category: Category.WARNING,
        codePointers: [{path: 'test/path.ts', range: movedRange}],
      } as RunResult;
      element.result = result;

      const originalComment = createComment({
        id: 'c1' as UrlEncodedCommentId,
        path: 'test/path.ts',
        patch_set: 1 as RevisionPatchSetNum,
        line: 12,
        range: originalRange,
        unresolved: true,
        message: pleaseFixMessage(result),
      });
      const commentsModel = testResolver(commentsModelToken);
      commentsModel.modifyState(s => {
        s = setComments(s, {'test/path.ts': [originalComment]});
        return setPortedComments(s, {
          'test/path.ts': [
            {
              ...originalComment,
              line: 17,
              range: movedRange,
            },
          ],
        });
      });
      await element.updateComplete;

      assert.isUndefined(query(element, '#please-fix'));
      assert.isUndefined(query(element, '#respond'));
    });

    test('keeps buttons when comment thread at location does not contain check text in first message', async () => {
      isEnabledStub
        .withArgs(KnownExperimentId.RESPOND_TO_CHECK_WARNINGS)
        .returns(true);
      const range = {
        start_line: 10,
        start_character: 0,
        end_line: 12,
        end_character: 5,
      };
      const result = {
        ...checkRun1,
        ...checkRun1.results?.[0],
        patchset: 1,
        category: Category.WARNING,
        codePointers: [{path: 'test/path.ts', range}],
      } as RunResult;
      element.result = result;

      const commentsModel = testResolver(commentsModelToken);
      commentsModel.modifyState(s =>
        setComments(s, {
          'test/path.ts': [
            createComment({
              id: 'c1' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              line: 12,
              range,
              unresolved: true,
              message: 'Unrelated comment',
            }),
            createComment({
              id: 'c2' as UrlEncodedCommentId,
              in_reply_to: 'c1' as UrlEncodedCommentId,
              patch_set: 1 as RevisionPatchSetNum,
              line: 12,
              range,
              unresolved: true,
              message: pleaseFixMessage(result),
            }),
          ],
        })
      );
      await element.updateComplete;

      assert.isDefined(query(element, '#please-fix'));
      assert.isDefined(query(element, '#respond'));
    });
  });
});
