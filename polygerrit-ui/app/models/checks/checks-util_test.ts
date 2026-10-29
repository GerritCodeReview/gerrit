/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './checks-model';
import {assert} from '@open-wc/testing';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  computeIsExpandable,
  createPleaseFixComment,
  createRespondComment,
  hasUnresolvedCheckCommentThread,
  LATEST_ATTEMPT,
  pleaseFixMessage,
  rectifyFix,
  reportAiAgentCommentDraft,
  reportAiAgentGetAIFix,
  respondMessage,
  RESPONSE_PLACEHOLDER,
  sortAttemptChoices,
  stringToAttemptChoice,
  toComment,
} from './checks-util';
import {Interaction} from '../../constants/reporting';
import {Category, Fix, Replacement} from '../../api/checks';
import {createCommentThreads, PROVIDED_FIX_ID} from '../../utils/comment-util';
import {
  CommentRange,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../api/rest-api';
import {ReportingService} from '../../services/gr-reporting/gr-reporting';
import {
  createCheckFix,
  createCheckLink,
  createCheckResult,
  createComment,
  createRange,
  createRunResult,
} from '../../test/test-data-generators';
import {RunResult} from './checks-model';

suite('checks-util tests', () => {
  setup(() => {});

  teardown(() => {});

  test('stringToAttemptChoice', () => {
    assert.equal(stringToAttemptChoice('0'), 0);
    assert.equal(stringToAttemptChoice('1'), 1);
    assert.equal(stringToAttemptChoice('999'), 999);
    assert.equal(stringToAttemptChoice('latest'), 'latest');
    assert.equal(stringToAttemptChoice('all'), 'all');

    assert.equal(stringToAttemptChoice(undefined), undefined);
    assert.equal(stringToAttemptChoice(''), undefined);
    assert.equal(stringToAttemptChoice('asdf'), undefined);
    assert.equal(stringToAttemptChoice('-1'), undefined);
    assert.equal(stringToAttemptChoice('1x'), undefined);
  });

  test('rectifyFix', () => {
    assert.isUndefined(rectifyFix(undefined, 'name'));
    assert.isUndefined(rectifyFix({} as Fix, 'name'));
    assert.isUndefined(
      rectifyFix({description: 'asdf', replacements: []}, 'name')
    );
    assert.isUndefined(
      rectifyFix(
        {description: 'asdf', replacements: [{} as Replacement]},
        'test-check-name'
      )
    );
    assert.isUndefined(
      rectifyFix(
        {
          description: 'asdf',
          replacements: [
            {
              path: 'test-path',
              range: {} as CommentRange,
              replacement: 'test-replacement-string',
            },
          ],
        },
        'test-check-name'
      )
    );
    const rectified = rectifyFix(
      {
        replacements: [
          {
            path: 'test-path',
            range: {
              start_line: 1,
              end_line: 1,
              start_character: 0,
              end_character: 1,
            } as CommentRange,
            replacement: 'test-replacement-string',
          },
        ],
      },
      'test-check-name'
    );
    assert.isDefined(rectified);
    assert.equal(rectified?.description, 'Fix provided by test-check-name');
    assert.equal(rectified?.fix_id, PROVIDED_FIX_ID);
  });

  test('rectifyFix changes description when description is empty', () => {
    const rectified = rectifyFix(
      {
        replacements: [
          {
            path: 'test-path',
            range: {
              start_line: 1,
              end_line: 1,
              start_character: 0,
              end_character: 1,
            } as CommentRange,
            replacement: 'test-replacement-string',
          },
        ],
        description: '',
      },
      'test-check-name'
    );
    assert.isDefined(rectified);
    assert.equal(rectified?.description, 'Fix provided by test-check-name');
  });

  test('sortAttemptChoices', () => {
    const unsorted: (AttemptChoice | undefined)[] = [
      3,
      1,
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      undefined,
      0,
      999,
    ];
    const sortedExpected: (AttemptChoice | undefined)[] = [
      LATEST_ATTEMPT,
      ALL_ATTEMPTS,
      0,
      1,
      3,
      999,
      undefined,
    ];
    assert.deepEqual(unsorted.sort(sortAttemptChoices), sortedExpected);
  });

  suite('computeIsExpandable', () => {
    test('no message', () => {
      assert.isFalse(computeIsExpandable(createCheckResult()));
    });

    test('no summary', () => {
      assert.isTrue(
        computeIsExpandable({
          ...createCheckResult(),
          message: 'asdf',
          summary: undefined as unknown as string,
        })
      );
    });

    test('has message', () => {
      assert.isTrue(
        computeIsExpandable({...createCheckResult(), message: 'asdf'})
      );
    });

    test('has just one link', () => {
      assert.isFalse(
        computeIsExpandable({
          ...createCheckResult(),
          links: [createCheckLink()],
        })
      );
    });

    test('has more than one link', () => {
      assert.isTrue(
        computeIsExpandable({
          ...createCheckResult(),
          links: [createCheckLink(), createCheckLink()],
        })
      );
    });

    test('has code pointer', () => {
      assert.isTrue(
        computeIsExpandable({
          ...createCheckResult(),
          codePointers: [{path: 'asdf', range: createRange()}],
        })
      );
    });

    test('has fix', () => {
      assert.isTrue(
        computeIsExpandable({
          ...createCheckResult(),
          fixes: [createCheckFix()],
        })
      );
    });
  });

  suite('toComment', () => {
    test('normal pointer', () => {
      const range = {
        start_line: 1,
        end_line: 2,
        start_character: 3,
        end_character: 4,
      };
      const result: RunResult = {
        ...createRunResult(),
        patchset: 3,
        codePointers: [
          {
            path: 'testpath',
            range,
          },
        ],
      };
      const comment = toComment(result);
      assert.equal(comment.patch_set, 3 as RevisionPatchSetNum);
      assert.equal(comment.range, range);
      assert.equal(comment.line, 2);
    });

    test('pointer with 0 range', () => {
      const range = {
        start_line: 0,
        end_line: 0,
        start_character: 0,
        end_character: 0,
      };
      const result: RunResult = {
        ...createRunResult(),
        patchset: 3,
        codePointers: [
          {
            path: 'testpath',
            range,
          },
        ],
      };
      const comment = toComment(result);
      assert.isUndefined(comment.range);
      assert.isUndefined(comment.line);
    });
  });

  suite('AI agent reporting', () => {
    let reportInteractionStub: sinon.SinonStub;

    setup(() => {
      reportInteractionStub = sinon.stub();
    });

    test('reportAiAgentGetAIFix', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: JSON.stringify({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 1,
          suggestionId: 1,
        }),
      };

      reportAiAgentGetAIFix(reporting, runResult);

      assert.isTrue(reportInteractionStub.calledOnce);
      assert.equal(
        reportInteractionStub.lastCall.args[0],
        Interaction.AI_AGENT_GET_FIX_CLICKED
      );
      assert.deepEqual(reportInteractionStub.lastCall.args[1], {
        agentId: 'test-agent',
        conversationId: 'test-conv',
        turnIndex: 1,
        suggestionId: 1,
        commentId: undefined,
      });
    });

    test('reportAiAgentCommentDraft', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: JSON.stringify({
          agentId: 'test-agent',
          conversationId: 'test-conv',
          turnIndex: 2,
        }),
      };

      reportAiAgentCommentDraft(reporting, runResult, 'test-comment-id');

      assert.isTrue(reportInteractionStub.calledOnce);
      assert.equal(
        reportInteractionStub.lastCall.args[0],
        Interaction.AI_AGENT_SUGGESTION_TO_COMMENT
      );
      assert.deepEqual(reportInteractionStub.lastCall.args[1], {
        agentId: 'test-agent',
        conversationId: 'test-conv',
        turnIndex: 2,
        suggestionId: undefined,
        commentId: 'test-comment-id',
      });
    });

    test('does not report if externalId is missing', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
      };

      reportAiAgentGetAIFix(reporting, runResult);
      assert.isFalse(reportInteractionStub.called);

      reportAiAgentCommentDraft(reporting, runResult);
      assert.isFalse(reportInteractionStub.called);
    });

    test('does not report if isAiPowered is false or missing', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: false,
        externalId: 'some-id',
      };

      reportAiAgentGetAIFix(reporting, runResult);
      assert.isFalse(reportInteractionStub.called);

      const runResultMissing = {
        ...createRunResult(),
        isAiPowered: undefined,
        externalId: 'some-id',
      };

      reportAiAgentCommentDraft(reporting, runResultMissing);
      assert.isFalse(reportInteractionStub.called);
    });

    test('reports check event details if externalId is plain string (not chat JSON)', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: 'plain-string-external-id',
      };

      reportAiAgentGetAIFix(reporting, runResult);
      assert.isTrue(reportInteractionStub.calledOnce);
      assert.deepEqual(reportInteractionStub.lastCall.args[1], {
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: 'plain-string-external-id',
        commentId: undefined,
      });
    });

    test('reports check event details if externalId is JSON but missing chat fields', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: JSON.stringify({agentId: 'test-agent'}),
      };

      reportAiAgentGetAIFix(reporting, runResult);
      assert.isTrue(reportInteractionStub.calledOnce);
      assert.deepEqual(reportInteractionStub.lastCall.args[1], {
        checkName: 'test-check-name',
        checkDescription: 'test-description',
        externalId: runResult.externalId,
        commentId: undefined,
      });
    });

    test('does not report check event if checkDescription is missing', () => {
      const reporting = {
        reportInteraction: reportInteractionStub,
      } as unknown as ReportingService;
      const runResult = {
        ...createRunResult(),
        isAiPowered: true,
        checkName: 'test-check-name',
        checkDescription: undefined,
        externalId: 'plain-string-external-id',
      };

      reportAiAgentGetAIFix(reporting, runResult);
      assert.isFalse(reportInteractionStub.called);
    });
  });

  suite('pleaseFixMessage', () => {
    test('when summary and message are the same', () => {
      const result: RunResult = {
        ...createRunResult(),
        category: Category.WARNING,
        checkName: 'test-check-name',
        summary: 'this is the warning text',
        message: 'this is the warning text',
      };
      assert.equal(
        pleaseFixMessage(result),
        'Please fix this WARNING reported by test-check-name: this is the warning text'
      );
    });

    test('when summary and message are not the same', () => {
      const result: RunResult = {
        ...createRunResult(),
        category: Category.ERROR,
        checkName: 'test-check-name',
        summary: 'this is the summary text',
        message: 'this is the message body text',
      };
      assert.equal(
        pleaseFixMessage(result),
        'Please fix this ERROR reported by test-check-name: this is the summary text\n\nthis is the message body text'
      );
    });
  });

  suite('respondMessage and createRespondComment', () => {
    test('RESPONSE_PLACEHOLDER matches backend constant', () => {
      assert.equal(RESPONSE_PLACEHOLDER, '<REASON HERE>');
    });

    test('when summary and message are the same', () => {
      const result: RunResult = {
        ...createRunResult(),
        category: Category.WARNING,
        checkName: 'test-check-name',
        summary: 'this is the warning text',
        message: 'this is the warning text',
      };
      assert.equal(
        respondMessage(result),
        `${RESPONSE_PLACEHOLDER}\n\nIn response to WARNING reported by test-check-name: this is the warning text`
      );
    });

    test('when summary and message are not the same', () => {
      const result: RunResult = {
        ...createRunResult(),
        category: Category.WARNING,
        checkName: 'test-check-name',
        summary: 'this is the summary text',
        message: 'this is the message body text',
      };
      assert.equal(
        respondMessage(result),
        `${RESPONSE_PLACEHOLDER}\n\nIn response to WARNING reported by test-check-name: this is the summary text\n\nthis is the message body text`
      );
    });

    test('when message is undefined', () => {
      const result: RunResult = {
        ...createRunResult(),
        category: Category.WARNING,
        checkName: 'test-check-name',
        summary: 'this is the summary text',
        message: undefined,
      };
      assert.equal(
        respondMessage(result),
        `${RESPONSE_PLACEHOLDER}\n\nIn response to WARNING reported by test-check-name: this is the summary text`
      );
    });

    test('createRespondComment creates unresolved draft with respond message', () => {
      const range = {
        start_line: 1,
        end_line: 2,
        start_character: 3,
        end_character: 4,
      };
      const result: RunResult = {
        ...createRunResult(),
        patchset: 3,
        category: Category.WARNING,
        checkName: 'test-check-name',
        summary: 'this is the warning text',
        message: 'this is the warning text',
        codePointers: [{path: 'testpath', range}],
      };
      const comment = createRespondComment(result);
      assert.equal(comment.path, 'testpath');
      assert.equal(comment.patch_set, 3 as RevisionPatchSetNum);
      assert.equal(comment.line, 2);
      assert.deepEqual(comment.range, range);
      assert.isTrue(comment.unresolved);
      assert.equal(
        comment.message,
        `${RESPONSE_PLACEHOLDER}\n\nIn response to WARNING reported by test-check-name: this is the warning text`
      );
    });
  });

  suite('hasUnresolvedCheckCommentThread', () => {
    const range = {
      start_line: 10,
      end_line: 12,
      start_character: 0,
      end_character: 5,
    };
    const baseResult: RunResult = {
      ...createRunResult(),
      patchset: 2,
      category: Category.WARNING,
      checkName: 'test-check',
      summary: 'Summary warning',
      message: 'Detailed warning text',
      codePointers: [{path: 'src/foo.ts', range}],
    };

    test('returns true for unresolved thread created by Please Fix or Respond', () => {
      const pleaseFixThreads = createCommentThreads([
        createPleaseFixComment(baseResult),
      ]);
      assert.isTrue(
        hasUnresolvedCheckCommentThread(baseResult, pleaseFixThreads)
      );

      const respondDraft = {
        ...createRespondComment(baseResult),
        message:
          'Intentional.\n\nIn response to WARNING reported by test-check: Summary warning\n\nDetailed warning text',
      };
      const respondThreads = createCommentThreads([respondDraft]);
      assert.isTrue(
        hasUnresolvedCheckCommentThread(baseResult, respondThreads)
      );
    });

    test('returns false when thread is resolved', () => {
      const resolvedComment = createComment({
        id: 'c1' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range,
        unresolved: false,
        message: pleaseFixMessage(baseResult),
      });
      assert.isFalse(
        hasUnresolvedCheckCommentThread(
          baseResult,
          createCommentThreads([resolvedComment])
        )
      );

      const unresolvedRoot = {...resolvedComment, unresolved: true};
      const resolvingReply = createComment({
        id: 'c2' as UrlEncodedCommentId,
        in_reply_to: 'c1' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range,
        unresolved: false,
        message: 'Done',
      });
      assert.isFalse(
        hasUnresolvedCheckCommentThread(
          baseResult,
          createCommentThreads([unresolvedRoot, resolvingReply])
        )
      );
    });

    test('returns false when first comment does not contain check text even if reply does', () => {
      const root = createComment({
        id: 'c1' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range,
        unresolved: true,
        message: 'Unrelated comment',
      });
      const reply = createComment({
        id: 'c2' as UrlEncodedCommentId,
        in_reply_to: 'c1' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range,
        unresolved: true,
        message: 'Summary warning\n\nDetailed warning text',
      });
      const threads = createCommentThreads([root, reply]);
      assert.isFalse(hasUnresolvedCheckCommentThread(baseResult, threads));
    });

    test('returns false when location or range presence differs', () => {
      const otherRange = {...range, start_character: 2};
      const otherRangeComment = createComment({
        id: 'c1' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range: otherRange,
        unresolved: true,
        message: pleaseFixMessage(baseResult),
      });
      assert.isFalse(
        hasUnresolvedCheckCommentThread(
          baseResult,
          createCommentThreads([otherRangeComment])
        )
      );

      const lineOnlyComment = createComment({
        id: 'c2' as UrlEncodedCommentId,
        path: 'src/foo.ts',
        patch_set: 2 as RevisionPatchSetNum,
        line: 12,
        range: undefined,
        unresolved: true,
        message: pleaseFixMessage(baseResult),
      });
      assert.isFalse(
        hasUnresolvedCheckCommentThread(
          baseResult,
          createCommentThreads([lineOnlyComment])
        )
      );
    });
  });
});
