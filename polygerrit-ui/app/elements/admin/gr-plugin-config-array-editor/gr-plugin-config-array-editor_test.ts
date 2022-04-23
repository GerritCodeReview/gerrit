/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import {ConfigParameterInfoType} from '../../../constants/constants.js';
import '../../../test/common-test-setup-karma';
import './gr-plugin-config-array-editor';
import {GrPluginConfigArrayEditor} from './gr-plugin-config-array-editor';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {queryAll, queryAndAssert} from '../../../test/test-utils.js';
import {GrButton} from '../../shared/gr-button/gr-button.js';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-plugin-config-array-editor tests', () => {
  let element: GrPluginConfigArrayEditor;

  let dispatchStub: sinon.SinonStub;

  setup(async () => {
    element = await fixture<GrPluginConfigArrayEditor>(html`
      <gr-plugin-config-array-editor></gr-plugin-config-array-editor>
    `);
    element.pluginOption = {
      _key: 'test-key',
      info: {
        type: ConfigParameterInfoType.ARRAY,
        values: [],
      },
    };
  });

  suite('adding', () => {
    setup(() => {
      dispatchStub = sinon.stub(element, 'dispatchChanged');
    });

    test('with enter', async () => {
      element.newValue = '';
      await element.updateComplete;
      MockInteractions.pressAndReleaseKeyOn(
        queryAndAssert<HTMLInputElement>(element, '#input'),
        13
      ); // Enter
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(element, '#input').hasAttribute(
          'disabled'
        )
      );
      await element.updateComplete;

      assert.isFalse(dispatchStub.called);
      element.newValue = 'test';
      await element.updateComplete;

      MockInteractions.pressAndReleaseKeyOn(
        queryAndAssert<HTMLInputElement>(element, '#input'),
        13
      ); // Enter
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(element, '#input').hasAttribute(
          'disabled'
        )
      );
      await element.updateComplete;

      assert.isTrue(dispatchStub.called);
      assert.equal(dispatchStub.lastCall.args[0], 'test');
      assert.equal(element.newValue, '');
    });

    test('with add btn', async () => {
      element.newValue = '';
      queryAndAssert<GrButton>(element, '#addButton').click();
      await element.updateComplete;

      assert.isFalse(dispatchStub.called);

      element.newValue = 'test';
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#addButton').click();
      await element.updateComplete;

      assert.isTrue(dispatchStub.called);
      assert.equal(dispatchStub.lastCall.args[0], 'test');
      assert.equal(element.newValue, '');
    });
  });

  test('deleting', async () => {
    dispatchStub = sinon.stub(element, 'dispatchChanged');
    element.pluginOption = {
      _key: '',
      info: {type: ConfigParameterInfoType.ARRAY, values: ['test', 'test2']},
    };
    element.disabled = true;
    await element.updateComplete;

    const rows = queryAll(element, '.existingItems .row');
    assert.equal(rows.length, 2);
    const button = queryAndAssert<GrButton>(rows[0], 'gr-button');

    MockInteractions.tap(button);
    await element.updateComplete;

    assert.isFalse(dispatchStub.called);
    element.disabled = false;
    await element.updateComplete;

    button.click();
    await element.updateComplete;

    assert.isTrue(dispatchStub.called);
    assert.deepEqual(dispatchStub.lastCall.args[0], ['test2']);
  });

  test('dispatchChanged', () => {
    const eventStub = sinon.stub(element, 'dispatchEvent');
    element.dispatchChanged(['new-test-value']);

    assert.isTrue(eventStub.called);
    const {detail} = eventStub.lastCall.args[0] as CustomEvent;
    assert.equal(detail._key, 'test-key');
    assert.deepEqual(detail.info, {type: 'ARRAY', values: ['new-test-value']});
    assert.equal(detail.notifyPath, 'test-key.values');
  });
});
