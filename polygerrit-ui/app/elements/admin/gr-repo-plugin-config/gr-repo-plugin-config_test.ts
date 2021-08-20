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

import '../../../test/common-test-setup-karma';
import './gr-repo-plugin-config';
import {GrRepoPluginConfig} from './gr-repo-plugin-config';

const basicFixture = fixtureFromElement('gr-repo-plugin-config');

suite('gr-repo-plugin-config tests', () => {
  let element: GrRepoPluginConfig;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  test('_computePluginConfigOptions', () => {
    assert.deepEqual(element._computePluginConfigOptions(
        {config: {_key: 'testInfo'}}),
    [{_key: 'testKey', info: 'testInfo'}]);
  });

  test('_handleChange', () => {
    const eventStub = sinon.stub(element, 'dispatchEvent');
    element.pluginData = {
      name: 'testName',
      config: {plugin: {value: 'test'}},
    };
    element._handleChange({
      _key: 'plugin',
      info: {value: 'newTest'},
      notifyPath: 'plugin.value',
    });

    assert.isTrue(eventStub.called);

    const {detail} = eventStub.lastCall.args[0];
    assert.equal(detail.name, 'testName');
    assert.deepEqual(detail.config, {plugin: {value: 'newTest'}});
    assert.equal(detail.notifyPath, 'testName.plugin.value');
  });

  suite('option types', () => {
    let changeStub;
    let buildStub;

    setup(() => {
      changeStub = sinon.stub(element, '_handleChange');
      buildStub = sinon.stub(element, '_buildConfigChangeInfo');
    });

    test('ARRAY type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'test', type: 'ARRAY', editable: true}},
      };
      await flush();

      const editor = element.shadowRoot
          .querySelector('gr-plugin-config-array-editor');
      assert.ok(editor);
      element._handleArrayChange({detail: 'test'});
      assert.isTrue(changeStub.called);
      assert.equal(changeStub.lastCall.args[0], 'test');
    });

    test('BOOLEAN type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'true', type: 'BOOLEAN', editable: true}},
      };
      await flush();

      const toggle = element.shadowRoot
          .querySelector('paper-toggle-button');
      assert.ok(toggle);
      toggle.click();
      await flush();

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['false', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('INT/LONG/STRING type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'test', type: 'STRING', editable: true}},
      };
      await flush();

      const input = element.shadowRoot
          .querySelector('input');
      assert.ok(input);
      input.value = 'newTest';
      input.dispatchEvent(new Event('input'));
      await flush();

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['newTest', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('LIST type option', async () => {
      const permitted_values = ['test', 'newTest'];
      element.pluginData = {
        name: 'testName',
        config: {plugin:
          {value: 'test', type: 'LIST', editable: true, permitted_values},
        },
      };
      await flush();

      const select = element.shadowRoot
          .querySelector('select');
      assert.ok(select);
      select.value = 'newTest';
      select.dispatchEvent(new Event(
          'change', {bubbles: true, composed: true}));
      await flush();

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['newTest', 'plugin']);

      assert.isTrue(changeStub.called);
    });
  });

  test('_buildConfigChangeInfo', () => {
    element.pluginData = {
      name: 'testName',
      config: {plugin: {value: 'test'}},
    };
    const detail = element._buildConfigChangeInfo('newTest', 'plugin');
    assert.equal(detail._key, 'plugin');
    assert.deepEqual(detail.info, {value: 'newTest'});
    assert.equal(detail.notifyPath, 'plugin.value');
  });
});

