/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-confirm-rebase-dialog.js';

const basicFixture = fixtureFromElement('gr-confirm-rebase-dialog');

suite('gr-confirm-rebase-dialog tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('controls with parent and rebase on current available', () => {
    element.rebaseOnCurrent = true;
    element.hasParent = true;
    flush();
    assert.isTrue(element.$.rebaseOnParentInput.checked);
    assert.isFalse(element.$.rebaseOnParent.hasAttribute('hidden'));
    assert.isTrue(element.$.parentUpToDateMsg.hasAttribute('hidden'));
    assert.isFalse(element.$.rebaseOnTip.hasAttribute('hidden'));
    assert.isTrue(element.$.tipUpToDateMsg.hasAttribute('hidden'));
  });

  test('controls with parent rebase on current not available', () => {
    element.rebaseOnCurrent = false;
    element.hasParent = true;
    flush();
    assert.isTrue(element.$.rebaseOnTipInput.checked);
    assert.isTrue(element.$.rebaseOnParent.hasAttribute('hidden'));
    assert.isFalse(element.$.parentUpToDateMsg.hasAttribute('hidden'));
    assert.isFalse(element.$.rebaseOnTip.hasAttribute('hidden'));
    assert.isTrue(element.$.tipUpToDateMsg.hasAttribute('hidden'));
  });

  test('controls without parent and rebase on current available', () => {
    element.rebaseOnCurrent = true;
    element.hasParent = false;
    flush();
    assert.isTrue(element.$.rebaseOnTipInput.checked);
    assert.isTrue(element.$.rebaseOnParent.hasAttribute('hidden'));
    assert.isTrue(element.$.parentUpToDateMsg.hasAttribute('hidden'));
    assert.isFalse(element.$.rebaseOnTip.hasAttribute('hidden'));
    assert.isTrue(element.$.tipUpToDateMsg.hasAttribute('hidden'));
  });

  test('controls without parent rebase on current not available', () => {
    element.rebaseOnCurrent = false;
    element.hasParent = false;
    flush();
    assert.isTrue(element.$.rebaseOnOtherInput.checked);
    assert.isTrue(element.$.rebaseOnParent.hasAttribute('hidden'));
    assert.isTrue(element.$.parentUpToDateMsg.hasAttribute('hidden'));
    assert.isTrue(element.$.rebaseOnTip.hasAttribute('hidden'));
    assert.isFalse(element.$.tipUpToDateMsg.hasAttribute('hidden'));
  });

  test('input cleared on cancel or submit', () => {
    element._text = '123';
    element.$.confirmDialog.dispatchEvent(
        new CustomEvent('confirm', {
          composed: true, bubbles: true,
        }));
    assert.equal(element._text, '');

    element._text = '123';
    element.$.confirmDialog.dispatchEvent(
        new CustomEvent('cancel', {
          composed: true, bubbles: true,
        }));
    assert.equal(element._text, '');
  });

  test('_getSelectedBase', () => {
    element._text = '5fab321c';
    element.$.rebaseOnParentInput.checked = true;
    assert.equal(element._getSelectedBase(), null);
    element.$.rebaseOnParentInput.checked = false;
    element.$.rebaseOnTipInput.checked = true;
    assert.equal(element._getSelectedBase(), '');
    element.$.rebaseOnTipInput.checked = false;
    assert.equal(element._getSelectedBase(), element._text);
    element._text = '101: Test';
    assert.equal(element._getSelectedBase(), '101');
  });

  suite('parent suggestions', () => {
    let recentChanges;
    setup(() => {
      recentChanges = [
        {
          name: '123: my first awesome change',
          value: 123,
        },
        {
          name: '124: my second awesome change',
          value: 124,
        },
        {
          name: '245: my third awesome change',
          value: 245,
        },
      ];

      sinon.stub(element.$.restAPI, 'getChanges').returns(Promise.resolve(
          [
            {
              _number: 123,
              subject: 'my first awesome change',
            },
            {
              _number: 124,
              subject: 'my second awesome change',
            },
            {
              _number: 245,
              subject: 'my third awesome change',
            },
          ]
      ));
    });

    test('_getRecentChanges', () => {
      sinon.spy(element, '_getRecentChanges');
      return element._getRecentChanges()
          .then(() => {
            assert.deepEqual(element._recentChanges, recentChanges);
            assert.equal(element.$.restAPI.getChanges.callCount, 1);
            // When called a second time, should not re-request recent changes.
            element._getRecentChanges();
          })
          .then(() => {
            assert.equal(element._getRecentChanges.callCount, 2);
            assert.equal(element.$.restAPI.getChanges.callCount, 1);
          });
    });

    test('_filterChanges', () => {
      assert.equal(element._filterChanges('123', recentChanges).length, 1);
      assert.equal(element._filterChanges('12', recentChanges).length, 2);
      assert.equal(element._filterChanges('awesome', recentChanges).length,
          3);
      assert.equal(element._filterChanges('third', recentChanges).length,
          1);

      element.changeNumber = 123;
      assert.equal(element._filterChanges('123', recentChanges).length, 0);
      assert.equal(element._filterChanges('124', recentChanges).length, 1);
      assert.equal(element._filterChanges('awesome', recentChanges).length,
          2);
    });

    test('input text change triggers function', () => {
      sinon.spy(element, '_getRecentChanges');
      element.$.parentInput.noDebounce = true;
      MockInteractions.pressAndReleaseKeyOn(
          element.$.parentInput.$.input,
          13,
          null,
          'enter');
      element._text = '1';
      assert.isTrue(element._getRecentChanges.calledOnce);
      element._text = '12';
      assert.isTrue(element._getRecentChanges.calledTwice);
    });
  });
});

