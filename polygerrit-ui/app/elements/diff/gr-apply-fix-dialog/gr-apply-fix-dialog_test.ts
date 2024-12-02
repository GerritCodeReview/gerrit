/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-apply-fix-dialog';
import {
  NavigationService,
  navigationToken,
} from '../../core/gr-navigation/gr-navigation';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {GrApplyFixDialog} from './gr-apply-fix-dialog';
import {PatchSetNum, PatchSetNumber} from '../../../types/common';
import {
  createFixSuggestionInfo,
  createParsedChange,
  createRevisions,
  getCurrentRevision,
} from '../../../test/test-data-generators';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {DiffInfo} from '../../../types/diff';
import {OpenFixPreviewEventDetail} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {SinonStubbedMember} from 'sinon';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-apply-fix-dialog tests', () => {
  let element: GrApplyFixDialog;
  let setUrlStub: SinonStubbedMember<NavigationService['setUrl']>;

  const TWO_FIXES: OpenFixPreviewEventDetail = {
    patchNum: 2 as PatchSetNum,
    fixSuggestions: [
      createFixSuggestionInfo('fix_1'),
      createFixSuggestionInfo('fix_2'),
    ],
    onCloseFixPreviewCallbacks: [],
  };

  const ONE_FIX: OpenFixPreviewEventDetail = {
    patchNum: 2 as PatchSetNum,
    fixSuggestions: [createFixSuggestionInfo('fix_1')],
    onCloseFixPreviewCallbacks: [],
  };

  function getConfirmButton(): GrButton {
    return queryAndAssert(
      queryAndAssert(element, '#applyFixDialog'),
      '#confirm'
    );
  }

  async function open(detail: OpenFixPreviewEventDetail) {
    element.open(
      new CustomEvent<OpenFixPreviewEventDetail>('open-fix-preview', {
        detail,
      })
    );
    await element.updateComplete;
  }

  setup(async () => {
    setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
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
    element.latestPatchNum = change.revisions[change.current_revision]
      ._number as PatchSetNumber;
    element.change = change;
    element.diffPrefs = {
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
      sinon.stub(element.applyFixModal!, 'showModal');
    });

    test('dialog opens fetch and sets previews', async () => {
      await open(TWO_FIXES);
      assert.equal(element.currentFix!.fix_id, 'fix_1');
      assert.equal(element.currentPreviews.length, 2);
      const button = getConfirmButton();
      assert.isFalse(button.hasAttribute('disabled'));
      assert.equal(button.getAttribute('title'), '');
    });

    test('tooltip is hidden if apply fix is loading', async () => {
      element.isApplyFixLoading = true;
      await open(TWO_FIXES);
      const button = getConfirmButton();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.equal(button.getAttribute('title'), 'Fix is still loading ...');
    });
  });

  test('renders', async () => {
    await open(TWO_FIXES);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog id="applyFixModal" tabindex="-1" open="">
          <gr-dialog id="applyFixDialog" role="dialog" loading="">
            <div slot="header">Fix fix_1</div>
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
                <gr-icon icon="chevron_left"></gr-icon>
              </gr-button>
              <gr-button
                aria-disabled="false"
                id="nextFix"
                role="button"
                tabindex="0"
              >
                <gr-icon icon="chevron_right"></gr-icon>
              </gr-button>
            </div>
          </gr-dialog>
        </dialog>
      `,
      {ignoreAttributes: ['style']}
    );
  });

  test('next button state updated when suggestions changed', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(Promise.resolve({}));

    await open(ONE_FIX);
    await element.updateComplete;
    assert.notOk(element.nextFix);
    element.applyFixModal?.close();

    await open(TWO_FIXES);
    assert.ok(element.nextFix);
    assert.notOk(element.nextFix!.disabled);
  });

  test('preview endpoint throws error should reset dialog', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(
      Promise.reject(new Error('backend error'))
    );
    try {
      await open(TWO_FIXES);
    } catch (error) {
      // expected
    }
    assert.equal(element.currentFix, undefined);
  });

  test('apply fix button should call apply, navigate to change view and fire close', async () => {
    const applyRobotFixSuggestionStub = stubRestApi(
      'applyRobotFixSuggestion'
    ).returns(Promise.resolve(new Response(null, {status: 200})));
    element.currentFix = createFixSuggestionInfo('123');
    element.hasEdit = true;

    const closeFixPreviewEventSpy = sinon.spy();
    element.onCloseFixPreviewCallbacks.push(closeFixPreviewEventSpy);

    await element.handleApplyFix(new CustomEvent('confirm'));

    sinon.assert.calledOnceWithExactly(
      applyRobotFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      '123'
    );
    assert.isTrue(setUrlStub.called);
    assert.equal(setUrlStub.lastCall.firstArg, '/c/test-project/+/42/2..edit');

    sinon.assert.calledOnceWithExactly(closeFixPreviewEventSpy, true);
    // reset gr-apply-fix-dialog and close
    assert.equal(element.currentFix, undefined);
    assert.equal(element.currentPreviews.length, 0);
  });

  test('should not navigate to change view if incorect reponse', async () => {
    const applyRobotFixSuggestionStub = stubRestApi(
      'applyRobotFixSuggestion'
    ).returns(Promise.resolve(new Response(null, {status: 500})));
    element.currentFix = createFixSuggestionInfo('fix_123');

    await element.handleApplyFix(new CustomEvent('confirm'));

    sinon.assert.calledWithExactly(
      applyRobotFixSuggestionStub,
      element.change!._number,
      2 as PatchSetNum,
      'fix_123'
    );
    assert.isFalse(setUrlStub.called);
    assert.equal(element.isApplyFixLoading, false);
  });

  test('select fix forward and back of multiple suggested fixes', async () => {
    sinon.stub(element.applyFixModal!, 'showModal');

    await open(TWO_FIXES);
    element.onNextFixClick(new CustomEvent('click'));
    assert.equal(element.currentFix!.fix_id, 'fix_2');
    element.onPrevFixClick(new CustomEvent('click'));
    assert.equal(element.currentFix!.fix_id, 'fix_1');
  });

  test('server-error should throw for failed apply call', async () => {
    stubRestApi('applyRobotFixSuggestion').returns(
      Promise.reject(new Error('backend error'))
    );
    element.currentFix = createFixSuggestionInfo('fix_123');

    const closeFixPreviewEventSpy = sinon.spy();
    element.onCloseFixPreviewCallbacks.push(closeFixPreviewEventSpy);

    let expectedError;
    await element.handleApplyFix(new CustomEvent('click')).catch(e => {
      expectedError = e;
    });
    assert.isOk(expectedError);
    assert.isFalse(setUrlStub.called);
    sinon.assert.notCalled(closeFixPreviewEventSpy);
  });

  test('onCancel fires close with correct parameters', () => {
    const closeFixPreviewEventSpy = sinon.spy();
    element.onCloseFixPreviewCallbacks.push(closeFixPreviewEventSpy);
    element.onCancel(new CustomEvent('cancel'));
    sinon.assert.calledOnceWithExactly(closeFixPreviewEventSpy, false);
  });
});
