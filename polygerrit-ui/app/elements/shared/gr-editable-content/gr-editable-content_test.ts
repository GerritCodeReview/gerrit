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

import '../../../test/common-test-setup-karma';
import './gr-editable-content';
import {GrEditableContent} from './gr-editable-content';
import {queryAndAssert, stubStorage} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrButton} from '../gr-button/gr-button';

const basicFixture = fixtureFromElement('gr-editable-content');

suite('gr-editable-content tests', () => {
  let element: GrEditableContent;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('save event', () => {
    element.content = '';
    element.newContent = 'foo';
    const handler = sinon.spy();
    element.addEventListener('editable-content-save', handler);

    MockInteractions.tap(queryAndAssert(element, 'gr-button[primary]'));

    assert.isTrue(handler.called);
    assert.equal(handler.lastCall.args[0].detail.content, 'foo');
  });

  test('cancel event', () => {
    const handler = sinon.spy();
    element.addEventListener('editable-content-cancel', handler);

    MockInteractions.tap(queryAndAssert(element, 'gr-button.cancel-button'));

    assert.isTrue(handler.called);
  });

  test('enabling editing keeps old content', () => {
    element.content = 'current content';
    element.newContent = 'old content';
    element.editing = true;
    assert.equal(element.newContent, 'old content');
  });

  test('disabling editing does not update edit field contents', () => {
    element.content = 'current content';
    element.editing = true;
    element.newContent = 'stale content';
    element.editing = false;
    assert.equal(element.newContent, 'stale content');
  });

  test('zero width spaces are removed properly', () => {
    element.removeZeroWidthSpace = true;
    element.content = 'R=\u200Btest@google.com';
    element.editing = true;
    assert.equal(element.newContent, 'R=test@google.com');
  });

  suite('editing', () => {
    setup(() => {
      element.content = 'current content';
      element.editing = true;
    });

    test('save button is disabled initially', () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });

    test('save button is enabled when content changes', () => {
      element.newContent = 'new content';
      assert.isFalse(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });
  });

  suite('storageKey and related behavior', () => {
    let dispatchSpy: sinon.SinonSpy;

    setup(() => {
      element.content = 'current content';
      element.storageKey = 'test';
      dispatchSpy = sinon.spy(element, 'dispatchEvent');
    });

    test('editing toggled to true, has stored data', () => {
      stubStorage('getEditableContentItem').returns({
        message: 'stored content',
        updated: 0,
      });
      element.editing = true;

      assert.equal(element.newContent, 'stored content');
      assert.isTrue(dispatchSpy.called);
      assert.equal(dispatchSpy.firstCall.args[0].type, 'show-alert');
    });

    test('editing toggled to true, has no stored data', () => {
      stubStorage('getEditableContentItem').returns(null);
      element.editing = true;

      assert.equal(element.newContent, 'current content');
      assert.equal(dispatchSpy.firstCall.args[0].type, 'editing-changed');
    });

    test('edits are cached', () => {
      const storeStub = stubStorage('setEditableContentItem');
      const eraseStub = stubStorage('eraseEditableContentItem');
      element.editing = true;

      element.newContent = 'new content';
      flush();
      element.storeTask?.flush();

      assert.isTrue(storeStub.called);
      assert.deepEqual(
        [element.storageKey, element.newContent],
        storeStub.lastCall.args
      );

      element.newContent = '';
      flush();
      element.storeTask?.flush();

      assert.isTrue(eraseStub.called);
      assert.deepEqual([element.storageKey], eraseStub.lastCall.args);
    });
  });
});
