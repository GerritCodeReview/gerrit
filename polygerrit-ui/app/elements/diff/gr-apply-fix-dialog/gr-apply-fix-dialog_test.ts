/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-apply-fix-dialog';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrApplyFixDialog} from './gr-apply-fix-dialog';
import {
  BasePatchSetNum,
  EDIT,
  PatchSetNum,
  RobotCommentInfo,
  RobotId,
  RobotRunId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {Comment} from '../../../utils/comment-util';
import {
  createFixSuggestionInfo,
  createParsedChange,
  createRevisions,
  getCurrentRevision,
} from '../../../test/test-data-generators';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {DiffInfo} from '../../../types/diff';
import {
  CloseFixPreviewEventDetail,
  EventType,
  OpenFixPreviewEventDetail,
} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-apply-fix-dialog tests', () => {
  let element: GrApplyFixDialog;

  const ROBOT_COMMENT_WITH_TWO_FIXES: RobotCommentInfo = {
    id: '1' as UrlEncodedCommentId,
    updated: '2018-02-08 18:49:18.000000000' as Timestamp,
    robot_id: 'robot_1' as RobotId,
    robot_run_id: 'run_1' as RobotRunId,
    properties: {},
    fix_suggestions: [
      createFixSuggestionInfo('fix_1'),
      createFixSuggestionInfo('fix_2'),
    ],
  };

  const ROBOT_COMMENT_WITH_ONE_FIX: RobotCommentInfo = {
    id: '2' as UrlEncodedCommentId,
    updated: '2018-02-08 18:49:18.000000000' as Timestamp,
    robot_id: 'robot_1' as RobotId,
    robot_run_id: 'run_1' as RobotRunId,
    properties: {},
    fix_suggestions: [createFixSuggestionInfo('fix_1')],
  };

  function getConfirmButton(): GrButton {
    return queryAndAssert(
      queryAndAssert(element, '#applyFixDialog'),
      '#confirm'
    );
  }

  async function open(comment: Comment) {
    await element.open(
      new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
        detail: {
          patchNum: 2 as PatchSetNum,
          comment,
        },
      })
    );
    await element.updateComplete;
  }

  setup(async () => {
    element = await fixture<GrApplyFixDialog>(
      html`<gr-apply-fix-dialog></gr-apply-fix-dialog>`
    );
    const change = {
      ...createParsedChange(),
      revisions: createRevisions(2),
      current_revision: getCurrentRevision(1),
    };
    element.changeNum = change._number;
    element.patchNum = change.revisions[change.current_revision]._number;
    element.change = change;
    element.prefs = {
      ...createDefaultDiffPrefs(),
      font_size: 12,
      line_length: 100,
      tab_size: 4,
    };
    await element.updateComplete;
  });

  suite('dialog open', () => {
    setup(() => {
      const diffInfo1: DiffInfo = {
        meta_a: {
          name: 'f1',
          content_type: 'text',
          lines: 10,
        },
        meta_b: {
          name: 'f1',
          content_type: 'text',
          lines: 12,
        },
        content: [
          {
            ab: ['loqlwkqll'],
          },
          {
            b: ['qwqqsqw'],
          },
          {
            ab: ['qwqqsqw', 'qweqeqweqeq', 'qweqweq'],
          },
        ],
        change_type: 'MODIFIED',
        intraline_status: 'OK',
      };

      const diffInfo2: DiffInfo = {
        meta_a: {
          name: 'f2',
          content_type: 'text',
          lines: 10,
        },
        meta_b: {
          name: 'f2',
          content_type: 'text',
          lines: 12,
        },
        content: [
          {
            ab: ['eqweqweqwex'],
          },
          {
            b: ['zassdasd'],
          },
          {
            ab: ['zassdasd', 'dasdasda', 'asdasdad'],
          },
        ],
        change_type: 'MODIFIED',
        intraline_status: 'OK',
      };

      stubRestApi('getRobotCommentFixPreview').returns(
        Promise.resolve({
          f1: diffInfo1,
          f2: diffInfo2,
        })
      );
      sinon.stub(element.applyFixOverlay!, 'open').returns(Promise.resolve());
    });

    test('dialog opens fetch and sets previews', async () => {
      await open(ROBOT_COMMENT_WITH_TWO_FIXES);
      assert.equal(element.currentFix!.fix_id, 'fix_1');
      assert.equal(element.currentPreviews.length, 2);
      assert.equal(element.robotId, 'robot_1' as RobotId);
      const button = getConfirmButton();
      assert.isFalse(button.hasAttribute('disabled'));
      assert.equal(button.getAttribute('title'), '');
    });

    test('tooltip is hidden if apply fix is loading', async () => {
      element.isApplyFixLoading = true;
      await open(ROBOT_COMMENT_WITH_TWO_FIXES);
      const button = getConfirmButton();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.equal(button.getAttribute('title'), '');
    });

    test('apply fix button is disabled on older patchset', async () => {
      element.change = element.change = {
        ...createParsedChange(),
        revisions: createRevisions(2),
        current_revision: getCurrentRevision(0),
      };
      await open(ROBOT_COMMENT_WITH_TWO_FIXES);
      const button = getConfirmButton();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.equal(
        button.getAttribute('title'),
        'Fix can only be applied to the latest patchset'
      );
    });
  });

  test('renders', async () => {
    await open(ROBOT_COMMENT_WITH_TWO_FIXES);
    expect(element).shadowDom.to.equal(
      /* HTML */ `
        <gr-overlay id="applyFixOverlay" tabindex="-1" with-backdrop="">
          <gr-dialog id="applyFixDialog" role="dialog">
            <div slot="header">robot_1 - Fix fix_1</div>
            <div slot="main"></div>
            <div class="fix-picker" slot="footer">
              <span>Suggested fix 1 of 2</span>
              <gr-button
                aria-disabled="true"
                disabled=""
                id="prevFix"
                role="button"
                tabindex="-1"
              >
                <iron-icon icon="gr-icons:chevron-left"> </iron-icon>
              </gr-button>
              <gr-button
                aria-disabled="false"
                id="nextFix"
                role="button"
                tabindex="0"
              >
                <iron-icon icon="gr-icons:chevron-right"> </iron-icon>
              </gr-button>
            </div>
          </gr-dialog>
        </gr-overlay>
      `,
      {ignoreAttributes: ['style']}
    );
  });

  test('next button state updated when suggestions changed', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(Promise.resolve({}));
    sinon.stub(element.applyFixOverlay!, 'open').returns(Promise.resolve());

    await open(ROBOT_COMMENT_WITH_ONE_FIX);
    await element.updateComplete;
    assert.notOk(element.nextFix);
    await open(ROBOT_COMMENT_WITH_TWO_FIXES);
    assert.ok(element.nextFix);
    assert.notOk(element.nextFix!.disabled);
  });

  test('preview endpoint throws error should reset dialog', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(
      Promise.reject(new Error('backend error'))
    );
    try {
      await open(ROBOT_COMMENT_WITH_TWO_FIXES);
    } catch (error) {
      // expected
    }
    assert.equal(element.currentFix, undefined);
  });

  test('apply fix button should call apply, navigate to change view and fire close', async () => {
    const applyFixSuggestionStub = stubRestApi('applyFixSuggestion').returns(
      Promise.resolve(new Response(null, {status: 200}))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element.currentFix = createFixSuggestionInfo('123');

    const closeFixPreviewEventSpy = sinon.spy();
    // Element is recreated after each test, removeEventListener isn't required
    element.addEventListener(
      EventType.CLOSE_FIX_PREVIEW,
      closeFixPreviewEventSpy
    );
    await element.handleApplyFix(new CustomEvent('confirm'));

    sinon.assert.calledOnceWithExactly(
      applyFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      '123'
    );
    sinon.assert.calledWithExactly(navigateToChangeStub, element.change!, {
      patchNum: EDIT,
      basePatchNum: element.change!.revisions[2]._number as BasePatchSetNum,
    });

    sinon.assert.calledOnceWithExactly(
      closeFixPreviewEventSpy,
      new CustomEvent<CloseFixPreviewEventDetail>(EventType.CLOSE_FIX_PREVIEW, {
        detail: {
          fixApplied: true,
        },
      })
    );

    // reset gr-apply-fix-dialog and close
    assert.equal(element.currentFix, undefined);
    assert.equal(element.currentPreviews.length, 0);
  });

  test('should not navigate to change view if incorect reponse', async () => {
    const applyFixSuggestionStub = stubRestApi('applyFixSuggestion').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element.currentFix = createFixSuggestionInfo('fix_123');

    await element.handleApplyFix(new CustomEvent('confirm'));
    sinon.assert.calledWithExactly(
      applyFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      'fix_123'
    );
    assert.isTrue(navigateToChangeStub.notCalled);

    assert.equal(element.isApplyFixLoading, false);
  });

  test('select fix forward and back of multiple suggested fixes', async () => {
    sinon.stub(element.applyFixOverlay!, 'open').returns(Promise.resolve());

    await open(ROBOT_COMMENT_WITH_TWO_FIXES);
    element.onNextFixClick(new CustomEvent('click'));
    assert.equal(element.currentFix!.fix_id, 'fix_2');
    element.onPrevFixClick(new CustomEvent('click'));
    assert.equal(element.currentFix!.fix_id, 'fix_1');
  });

  test('server-error should throw for failed apply call', async () => {
    stubRestApi('applyFixSuggestion').returns(
      Promise.reject(new Error('backend error'))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element.currentFix = createFixSuggestionInfo('fix_123');

    const closeFixPreviewEventSpy = sinon.spy();
    // Element is recreated after each test, removeEventListener isn't required
    element.addEventListener(
      EventType.CLOSE_FIX_PREVIEW,
      closeFixPreviewEventSpy
    );

    let expectedError;
    await element.handleApplyFix(new CustomEvent('click')).catch(e => {
      expectedError = e;
    });
    assert.isOk(expectedError);
    assert.isFalse(navigateToChangeStub.called);
    sinon.assert.notCalled(closeFixPreviewEventSpy);
  });

  test('onCancel fires close with correct parameters', () => {
    const closeFixPreviewEventSpy = sinon.spy();
    // Element is recreated after each test, removeEventListener isn't required
    element.addEventListener(
      EventType.CLOSE_FIX_PREVIEW,
      closeFixPreviewEventSpy
    );
    element.onCancel(new CustomEvent('cancel'));
    sinon.assert.calledOnceWithExactly(
      closeFixPreviewEventSpy,
      new CustomEvent<CloseFixPreviewEventDetail>(EventType.CLOSE_FIX_PREVIEW, {
        detail: {
          fixApplied: false,
        },
      })
    );
  });
});
