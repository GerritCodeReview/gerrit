/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-apply-fix-dialog';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrApplyFixDialog} from './gr-apply-fix-dialog';
import {
  BasePatchSetNum,
  EditPatchSetNum,
  PatchSetNum,
  RobotCommentInfo,
  RobotId,
  RobotRunId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
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

const basicFixture = fixtureFromElement('gr-apply-fix-dialog');

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

  setup(() => {
    element = basicFixture.instantiate();
    const change = {
      ...createParsedChange(),
      revisions: createRevisions(2),
      current_revision: getCurrentRevision(1),
    };
    element.changeNum = change._number;
    element._patchNum = change.revisions[change.current_revision]._number;
    element.change = change;
    element.prefs = {
      ...createDefaultDiffPrefs(),
      font_size: 12,
      line_length: 100,
      tab_size: 4,
    };
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
      sinon.stub(element.$.applyFixOverlay, 'open').returns(Promise.resolve());
    });

    test('dialog opens fetch and sets previews', async () => {
      await element.open(
        new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
          detail: {
            patchNum: 2 as PatchSetNum,
            comment: ROBOT_COMMENT_WITH_TWO_FIXES,
          },
        })
      );
      assert.equal(element._currentFix!.fix_id, 'fix_1');
      assert.equal(element._currentPreviews.length, 2);
      assert.equal(element._robotId, 'robot_1' as RobotId);
      const button = getConfirmButton();
      assert.isFalse(button.hasAttribute('disabled'));
      assert.equal(button.getAttribute('title'), '');
    });

    test('tooltip is hidden if apply fix is loading', async () => {
      await element.open(
        new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
          detail: {
            patchNum: 2 as PatchSetNum,
            comment: ROBOT_COMMENT_WITH_TWO_FIXES,
          },
        })
      );
      element._isApplyFixLoading = true;
      await flush();
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
      await element.open(
        new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
          detail: {
            patchNum: 2 as PatchSetNum,
            comment: ROBOT_COMMENT_WITH_ONE_FIX,
          },
        })
      );
      await flush();
      const button = getConfirmButton();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.equal(
        button.getAttribute('title'),
        'Fix can only be applied to the latest patchset'
      );
    });
  });

  test('next button state updated when suggestions changed', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(Promise.resolve({}));
    sinon.stub(element.$.applyFixOverlay, 'open').returns(Promise.resolve());

    await element.open(
      new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
        detail: {
          patchNum: 2 as PatchSetNum,
          comment: ROBOT_COMMENT_WITH_ONE_FIX,
        },
      })
    );
    assert.isTrue(element.$.nextFix.disabled);
    await element.open(
      new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
        detail: {
          patchNum: 2 as PatchSetNum,
          comment: ROBOT_COMMENT_WITH_TWO_FIXES,
        },
      })
    );
    assert.isFalse(element.$.nextFix.disabled);
  });

  test('preview endpoint throws error should reset dialog', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(
      Promise.reject(new Error('backend error'))
    );
    element.open(
      new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
        detail: {
          patchNum: 2 as PatchSetNum,
          comment: ROBOT_COMMENT_WITH_TWO_FIXES,
        },
      })
    );
    await flush();
    assert.equal(element._currentFix, undefined);
  });

  test('apply fix button should call apply, navigate to change view and fire close', async () => {
    const applyFixSuggestionStub = stubRestApi('applyFixSuggestion').returns(
      Promise.resolve(new Response(null, {status: 200}))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = createFixSuggestionInfo('123');

    const closeFixPreviewEventSpy = sinon.spy();
    // Element is recreated after each test, removeEventListener isn't required
    element.addEventListener(
      EventType.CLOSE_FIX_PREVIEW,
      closeFixPreviewEventSpy
    );
    await element._handleApplyFix(new CustomEvent('confirm'));

    sinon.assert.calledOnceWithExactly(
      applyFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      '123'
    );
    sinon.assert.calledWithExactly(
      navigateToChangeStub,
      element.change!,
      EditPatchSetNum,
      element.change!.revisions[2]._number as BasePatchSetNum
    );

    sinon.assert.calledOnceWithExactly(
      closeFixPreviewEventSpy,
      new CustomEvent<CloseFixPreviewEventDetail>(EventType.CLOSE_FIX_PREVIEW, {
        detail: {
          fixApplied: true,
        },
      })
    );

    // reset gr-apply-fix-dialog and close
    assert.equal(element._currentFix, undefined);
    assert.equal(element._currentPreviews.length, 0);
  });

  test('should not navigate to change view if incorect reponse', async () => {
    const applyFixSuggestionStub = stubRestApi('applyFixSuggestion').returns(
      Promise.resolve(new Response(null, {status: 500}))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = createFixSuggestionInfo('fix_123');

    await element._handleApplyFix(new CustomEvent('confirm'));
    sinon.assert.calledWithExactly(
      applyFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      'fix_123'
    );
    assert.isTrue(navigateToChangeStub.notCalled);

    assert.equal(element._isApplyFixLoading, false);
  });

  test('select fix forward and back of multiple suggested fixes', async () => {
    sinon.stub(element.$.applyFixOverlay, 'open').returns(Promise.resolve());

    await element.open(
      new CustomEvent<OpenFixPreviewEventDetail>(EventType.OPEN_FIX_PREVIEW, {
        detail: {
          patchNum: 2 as PatchSetNum,
          comment: ROBOT_COMMENT_WITH_TWO_FIXES,
        },
      })
    );
    element._onNextFixClick(new CustomEvent('click'));
    assert.equal(element._currentFix!.fix_id, 'fix_2');
    element._onPrevFixClick(new CustomEvent('click'));
    assert.equal(element._currentFix!.fix_id, 'fix_1');
  });

  test('server-error should throw for failed apply call', async () => {
    stubRestApi('applyFixSuggestion').returns(
      Promise.reject(new Error('backend error'))
    );
    const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = createFixSuggestionInfo('fix_123');

    const closeFixPreviewEventSpy = sinon.spy();
    // Element is recreated after each test, removeEventListener isn't required
    element.addEventListener(
      EventType.CLOSE_FIX_PREVIEW,
      closeFixPreviewEventSpy
    );

    let expectedError;
    await element._handleApplyFix(new CustomEvent('click')).catch(e => {
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
