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

import '../../../test/common-test-setup-karma.js';
import './gr-apply-fix-dialog.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-apply-fix-dialog');

suite('gr-apply-fix-dialog tests', () => {
  let element;

  const ROBOT_COMMENT_WITH_TWO_FIXES = {
    robot_id: 'robot_1',
    fix_suggestions: [{fix_id: 'fix_1'}, {fix_id: 'fix_2'}],
  };

  const ROBOT_COMMENT_WITH_ONE_FIX = {
    robot_id: 'robot_1',
    fix_suggestions: [{fix_id: 'fix_1'}],
  };

  setup(() => {
    element = basicFixture.instantiate();
    element.changeNum = '1';
    element._patchNum = 2;
    element.change = {
      _number: '1',
      project: 'project',
      revisions: {
        abcd: {_number: 1},
        efgh: {_number: 2},
      },
      current_revision: 'efgh',
    };
    element.prefs = {
      font_size: 12,
      line_length: 100,
      tab_size: 4,
    };
  });

  suite('dialog open', () => {
    setup(() => {
      stubRestApi('getRobotCommentFixPreview')
          .returns(Promise.resolve({
            f1: {
              meta_a: {},
              meta_b: {},
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
            },
            f2: {
              meta_a: {},
              meta_b: {},
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
            },
          }));
      sinon.stub(element.$.applyFixOverlay, 'open')
          .returns(Promise.resolve());
    });

    test('dialog opens fetch and sets previews', done => {
      element.open({detail: {patchNum: 2,
        comment: ROBOT_COMMENT_WITH_TWO_FIXES}})
          .then(() => {
            assert.equal(element._currentFix.fix_id, 'fix_1');
            assert.equal(element._currentPreviews.length, 2);
            assert.equal(element._robotId, 'robot_1');
            const button = element.shadowRoot.querySelector(
                '#applyFixDialog').shadowRoot.querySelector('#confirm');
            assert.isFalse(button.hasAttribute('disabled'));
            assert.equal(button.getAttribute('title'), '');
            done();
          });
    });

    test('tooltip is hidden if apply fix is loading', done => {
      element.open({detail: {patchNum: 2,
        comment: ROBOT_COMMENT_WITH_TWO_FIXES}})
          .then(() => {
            element._isApplyFixLoading = true;
            const button = element.shadowRoot.querySelector(
                '#applyFixDialog').shadowRoot.querySelector('#confirm');
            assert.isTrue(button.hasAttribute('disabled'));
            assert.equal(button.getAttribute('title'), '');
            done();
          });
    });

    test('apply fix button is disabled on older patchset', done => {
      element.change = {
        _number: '1',
        project: 'project',
        revisions: {
          abcd: {_number: 1},
          efgh: {_number: 2},
        },
        current_revision: 'abcd',
      };
      element.open({detail: {patchNum: 2,
        comment: ROBOT_COMMENT_WITH_ONE_FIX}})
          .then(() => {
            flush(() => {
              const button = element.shadowRoot.querySelector(
                  '#applyFixDialog').shadowRoot.querySelector('#confirm');
              assert.isTrue(button.hasAttribute('disabled'));
              assert.equal(button.getAttribute('title'),
                  'Fix can only be applied to the latest patchset');
              done();
            });
          });
    });
  });

  test('next button state updated when suggestions changed', done => {
    stubRestApi('getRobotCommentFixPreview').returns(Promise.resolve({}));
    sinon.stub(element.$.applyFixOverlay, 'open').returns(Promise.resolve());

    element.open({detail: {patchNum: 2, comment: ROBOT_COMMENT_WITH_ONE_FIX}})
        .then(() => assert.isTrue(element.$.nextFix.disabled))
        .then(() =>
          element.open({detail: {patchNum: 2,
            comment: ROBOT_COMMENT_WITH_TWO_FIXES}}))
        .then(() => {
          assert.isFalse(element.$.nextFix.disabled);
          done();
        });
  });

  test('preview endpoint throws error should reset dialog', async () => {
    stubRestApi('getRobotCommentFixPreview').returns(
        Promise.reject(new Error('backend error')));
    element.open({detail: {patchNum: 2,
      comment: ROBOT_COMMENT_WITH_TWO_FIXES}});
    await flush();
    assert.equal(element._currentFix, undefined);
  });

  test('apply fix button should call apply ' +
  'and navigate to change view', () => {
    const stub = stubRestApi('applyFixSuggestion').returns(
        Promise.resolve({ok: true}));
    sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = {fix_id: '123'};

    return element._handleApplyFix().then(() => {
      assert.isTrue(stub.calledWithExactly('1', 2, '123'));
      assert.isTrue(GerritNav.navigateToChange.calledWithExactly({
        _number: '1',
        project: 'project',
        revisions: {
          abcd: {_number: 1},
          efgh: {_number: 2},
        },
        current_revision: 'efgh',
      }, 'edit', 2));

      // reset gr-apply-fix-dialog and close
      assert.equal(element._currentFix, undefined);
      assert.equal(element._currentPreviews.length, 0);
    });
  });

  test('should not navigate to change view if incorect reponse', done => {
    const stub = stubRestApi('applyFixSuggestion').returns(Promise.resolve({}));
    sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = {fix_id: '123'};

    element._handleApplyFix().then(() => {
      assert.isTrue(stub.calledWithExactly('1', 2, '123'));
      assert.isTrue(GerritNav.navigateToChange.notCalled);

      assert.equal(element._isApplyFixLoading, false);
      done();
    });
  });

  test('select fix forward and back of multiple suggested fixes', done => {
    stubRestApi('getRobotCommentFixPreview')
        .returns(Promise.resolve({
          f1: {
            meta_a: {},
            meta_b: {},
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
          },
          f2: {
            meta_a: {},
            meta_b: {},
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
          },
        }));
    sinon.stub(element.$.applyFixOverlay, 'open').returns(Promise.resolve());

    element.open({detail: {patchNum: 2, comment: ROBOT_COMMENT_WITH_TWO_FIXES}})
        .then(() => {
          element._onNextFixClick();
          assert.equal(element._currentFix.fix_id, 'fix_2');
          element._onPrevFixClick();
          assert.equal(element._currentFix.fix_id, 'fix_1');
          done();
        });
  });

  test('server-error should throw for failed apply call', async () => {
    stubRestApi('applyFixSuggestion').returns(
        Promise.reject(new Error('backend error')));
    sinon.stub(GerritNav, 'navigateToChange');
    element._currentFix = {fix_id: '123'};
    let expectedError;
    await element._handleApplyFix().catch(e => {
      expectedError = e;
    });
    assert.isOk(expectedError);
    assert.isFalse(GerritNav.navigateToChange.called);
  });
});

