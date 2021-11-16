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

import '../../../test/common-test-setup-karma.js';
import './gr-repo-plugin-config.js';

const basicFixture = fixtureFromElement('gr-repo-plugin-config');

suite('gr-repo-plugin-config tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computePluginConfigOptions', () => {
    assert.deepEqual(element._computePluginConfigOptions(), []);
    assert.deepEqual(element._computePluginConfigOptions({}), []);
    assert.deepEqual(element._computePluginConfigOptions({base: {}}), []);
    assert.deepEqual(element._computePluginConfigOptions(
        {base: {config: {}}}), []);
    assert.deepEqual(element._computePluginConfigOptions(
        {base: {config: {testKey: 'testInfo'}}}),
    [{_key: 'testKey', info: 'testInfo'}]);
  });

  test('_computeDisabled', () => {
    assert.isFalse(element._computeDisabled(false, true));
    assert.isTrue(element._computeDisabled(false, undefined));
    assert.isTrue(element._computeDisabled(false, null));
    assert.isTrue(element._computeDisabled(false, false));
    assert.isTrue(element._computeDisabled(true, true));
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

    test('ARRAY type option', () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'test', type: 'ARRAY', editable: true}},
      };
      flush();

      const editor = element.shadowRoot
          .querySelector('gr-plugin-config-array-editor');
      assert.ok(editor);
      element._handleArrayChange({detail: 'test'});
      assert.isTrue(changeStub.called);
      assert.equal(changeStub.lastCall.args[0], 'test');
    });

    test('BOOLEAN type option', () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'true', type: 'BOOLEAN', editable: true}},
      };
      flush();

      const toggle = element.shadowRoot
          .querySelector('paper-toggle-button');
      assert.ok(toggle);
      toggle.click();
      flush();

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['false', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('INT/LONG/STRING type option', () => {
      element.pluginData = {
        name: 'testName',
        config: {plugin: {value: 'test', type: 'STRING', editable: true}},
      };
      flush();

      const input = element.shadowRoot
          .querySelector('input');
      assert.ok(input);
      input.value = 'newTest';
      input.dispatchEvent(new Event('input'));
      flush();

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['newTest', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('LIST type option', () => {
      const permitted_values = ['test', 'newTest'];
      element.pluginData = {
        name: 'testName',
        config: {plugin:
          {value: 'test', type: 'LIST', editable: true, permitted_values},
        },
      };
      flush();

      const select = element.shadowRoot
          .querySelector('select');
      assert.ok(select);
      select.value = 'newTest';
      select.dispatchEvent(new Event(
          'change', {bubbles: true, composed: true}));
      flush();

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

