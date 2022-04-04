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
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-editable-content tests', () => {
  let element: GrEditableContent;

  setup(async () => {
    element = await fixture(html`<gr-editable-content></gr-editable-content>`);
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<gr-endpoint-decorator
      name="commit-message"
    >
      <gr-endpoint-param name="editing"> </gr-endpoint-param>
      <div class="collapsed viewer">
        <slot> </slot>
      </div>
      <div class="editor" hidden="">
        <div>
          <iron-autogrow-textarea aria-disabled="false" autocomplete="on">
          </iron-autogrow-textarea>
        </div>
      </div>
      <div class="show-all-container">
        <gr-button
          aria-disabled="false"
          class="show-all-button"
          role="button"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:expand-more"> </iron-icon>
          <iron-icon hidden="" icon="gr-icons:expand-less"> </iron-icon>
          Show all
        </gr-button>
        <gr-button
          aria-disabled="false"
          class="edit-commit-message"
          link=""
          role="button"
          tabindex="0"
          title="Edit commit message"
        >
          <iron-icon icon="gr-icons:edit"> </iron-icon>
          Edit
        </gr-button>
        <div class="editButtons" hidden="">
          <gr-button
            aria-disabled="false"
            class="cancel-button"
            link=""
            role="button"
            tabindex="0"
          >
            Cancel
          </gr-button>
          <gr-button
            aria-disabled="true"
            class="save-button"
            disabled=""
            primary=""
            role="button"
            tabindex="-1"
          >
            Save
          </gr-button>
        </div>
      </div>
      <gr-endpoint-slot name="above-actions"> </gr-endpoint-slot>
    </gr-endpoint-decorator> `);
  });

  test('save event', async () => {
    element.content = '';
    // Needed because contentChanged resets newContent
    await element.updateComplete;

    element.newContent = 'foo';
    element.disabled = false;
    const handler = sinon.spy();
    element.addEventListener('editable-content-save', handler);

    await element.updateComplete;

    queryAndAssert<GrButton>(element, 'gr-button[primary]').click();

    await element.updateComplete;

    assert.isTrue(handler.called);
    assert.equal(handler.lastCall.args[0].detail.content, 'foo');
  });

  test('cancel event', () => {
    const handler = sinon.spy();
    element.addEventListener('editable-content-cancel', handler);

    MockInteractions.tap(queryAndAssert(element, 'gr-button.cancel-button'));

    assert.isTrue(handler.called);
  });

  test('enabling editing keeps old content', async () => {
    element.content = 'current content';

    // Needed because contentChanged resets newContent
    await element.updateComplete;

    element.newContent = 'old content';
    element.editing = true;

    await element.updateComplete;

    assert.equal(element.newContent, 'old content');
  });

  test('disabling editing does not update edit field contents', () => {
    element.content = 'current content';
    element.editing = true;
    element.newContent = 'stale content';
    element.editing = false;
    assert.equal(element.newContent, 'stale content');
  });

  test('zero width spaces are removed properly', async () => {
    element.removeZeroWidthSpace = true;
    element.content = 'R=\u200Btest@google.com';

    // Needed because contentChanged resets newContent
    await element.updateComplete;

    element.editing = true;

    // editingChanged updates newContent so wait for it's observer
    // to finish
    await element.updateComplete;

    assert.equal(element.newContent, 'R=test@google.com');
  });

  suite('editing', () => {
    setup(async () => {
      element.content = 'current content';
      // Needed because contentChanged resets newContent
      // contentChanged updates newContent as well so wait for that observer
      // to finish before setting editing=true.
      await element.updateComplete;
      element.editing = true;
      await element.updateComplete;
    });

    test('save button is disabled initially', () => {
      assert.isTrue(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });

    test('save button is enabled when content changes', async () => {
      element.newContent = 'new content';
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<GrButton>(element, 'gr-button[primary]').disabled
      );
    });
  });

  suite('storageKey and related behavior', () => {
    let dispatchSpy: sinon.SinonSpy;

    setup(async () => {
      element.content = 'current content';
      // Needed because contentChanged resets newContent
      await element.updateComplete;
      element.storageKey = 'test';
      dispatchSpy = sinon.spy(element, 'dispatchEvent');
    });

    test('editing toggled to true, has stored data', async () => {
      stubStorage('getEditableContentItem').returns({
        message: 'stored content',
        updated: 0,
      });
      element.editing = true;

      await element.updateComplete;

      assert.equal(element.newContent, 'stored content');
      assert.isTrue(dispatchSpy.called);
      assert.equal(dispatchSpy.firstCall.args[0].type, 'show-alert');
    });

    test('editing toggled to true, has no stored data', async () => {
      stubStorage('getEditableContentItem').returns(null);
      element.editing = true;

      await element.updateComplete;

      assert.equal(element.newContent, 'current content');
      assert.equal(dispatchSpy.firstCall.args[0].type, 'editing-changed');
    });

    test('edits are cached', async () => {
      const storeStub = stubStorage('setEditableContentItem');
      const eraseStub = stubStorage('eraseEditableContentItem');
      element.editing = true;

      await element.updateComplete;

      element.newContent = 'new content';

      await element.updateComplete;

      element.storeTask?.flush();

      assert.isTrue(storeStub.called);
      assert.deepEqual(
        [element.storageKey, element.newContent],
        storeStub.lastCall.args
      );

      element.newContent = '';

      await element.updateComplete;

      element.storeTask?.flush();

      assert.isTrue(eraseStub.called);
      assert.deepEqual([element.storageKey], eraseStub.lastCall.args);
    });
  });
});
