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
import '../../change/gr-change-actions/gr-change-actions.js';
import {resetPlugins} from '../../../test/test-utils.js';
import {getPluginLoader} from './gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const basicFixture = fixtureFromElement('gr-change-actions');

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-change-actions-js-api-interface tests', () => {
  let element;
  let changeActions;
  let plugin;

  // Because deepEqual doesn’t behave in Safari.
  function assertArraysEqual(actual, expected) {
    assert.equal(actual.length, expected.length);
    for (let i = 0; i < actual.length; i++) {
      assert.equal(actual[i], expected[i]);
    }
  }

  suite('early init', () => {
    setup(() => {
      resetPlugins();
      pluginApi.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      // Mimic all plugins loaded.
      getPluginLoader().loadPlugins([]);
      changeActions = plugin.changeActions();
      element = basicFixture.instantiate();
    });

    teardown(() => {
      changeActions = null;
      resetPlugins();
    });

    test('does not throw', ()=> {
      assert.doesNotThrow(() => {
        changeActions.add('change', 'foo');
      });
    });
  });

  suite('normal init', () => {
    setup(() => {
      resetPlugins();
      element = basicFixture.instantiate();
      sinon.stub(element, '_editStatusChanged');
      element.change = {};
      element._hasKnownChainState = false;
      pluginApi.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      changeActions = plugin.changeActions();
      // Mimic all plugins loaded.
      getPluginLoader().loadPlugins([]);
    });

    teardown(() => {
      changeActions = null;
      resetPlugins();
    });

    test('property existence', () => {
      const properties = [
        'ActionType',
        'ChangeActions',
        'RevisionActions',
      ];
      for (const p of properties) {
        assertArraysEqual(changeActions[p], element[p]);
      }
    });

    test('add/remove primary action keys', () => {
      element.primaryActionKeys = [];
      changeActions.addPrimaryActionKey('foo');
      assertArraysEqual(element.primaryActionKeys, ['foo']);
      changeActions.addPrimaryActionKey('foo');
      assertArraysEqual(element.primaryActionKeys, ['foo']);
      changeActions.addPrimaryActionKey('bar');
      assertArraysEqual(element.primaryActionKeys, ['foo', 'bar']);
      changeActions.removePrimaryActionKey('foo');
      assertArraysEqual(element.primaryActionKeys, ['bar']);
      changeActions.removePrimaryActionKey('baz');
      assertArraysEqual(element.primaryActionKeys, ['bar']);
      changeActions.removePrimaryActionKey('bar');
      assertArraysEqual(element.primaryActionKeys, []);
    });

    test('action buttons', () => {
      const key = changeActions.add(changeActions.ActionType.REVISION, 'Bork!');
      const handler = sinon.spy();
      changeActions.addTapListener(key, handler);
      flush();
      MockInteractions.tap(element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]'));
      assert(handler.calledOnce);
      changeActions.removeTapListener(key, handler);
      MockInteractions.tap(element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]'));
      assert(handler.calledOnce);
      changeActions.remove(key);
      flush();
      assert.isNull(element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]'));
    });

    test('action button properties', () => {
      const key = changeActions.add(changeActions.ActionType.REVISION, 'Bork!');
      flush();
      const button = element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]');
      assert.isOk(button);
      assert.equal(button.getAttribute('data-label'), 'Bork!');
      assert.isNotOk(button.disabled);
      changeActions.setLabel(key, 'Yo');
      changeActions.setTitle(key, 'Yo hint');
      changeActions.setEnabled(key, false);
      changeActions.setIcon(key, 'pupper');
      flush();
      assert.equal(button.getAttribute('data-label'), 'Yo');
      assert.equal(button.getAttribute('title'), 'Yo hint');
      assert.isTrue(button.disabled);
      assert.equal(button.querySelector('iron-icon').icon,
          'gr-icons:pupper');
    });

    test('hide action buttons', () => {
      const key = changeActions.add(changeActions.ActionType.REVISION, 'Bork!');
      flush();
      let button = element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]');
      assert.isOk(button);
      assert.isFalse(button.hasAttribute('hidden'));
      changeActions.setActionHidden(
          changeActions.ActionType.REVISION, key, true);
      flush();
      button = element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]');
      assert.isNotOk(button);
    });

    test('move action button to overflow', async () => {
      const key = changeActions.add(changeActions.ActionType.REVISION, 'Bork!');
      await flush();
      assert.isTrue(element.$.moreActions.hidden);
      assert.isOk(element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]'));
      changeActions.setActionOverflow(
          changeActions.ActionType.REVISION, key, true);
      flush();
      assert.isNotOk(element.shadowRoot
          .querySelector('[data-action-key="' + key + '"]'));
      assert.isFalse(element.$.moreActions.hidden);
      assert.strictEqual(element.$.moreActions.items[0].name, 'Bork!');
    });

    test('change actions priority', () => {
      const key1 =
        changeActions.add(changeActions.ActionType.REVISION, 'Bork!');
      const key2 =
        changeActions.add(changeActions.ActionType.CHANGE, 'Squanch?');
      flush();
      let buttons =
        element.root.querySelectorAll('[data-action-key]');
      assert.equal(buttons[0].getAttribute('data-action-key'), key1);
      assert.equal(buttons[1].getAttribute('data-action-key'), key2);
      changeActions.setActionPriority(
          changeActions.ActionType.REVISION, key1, 10);
      flush();
      buttons =
        element.root.querySelectorAll('[data-action-key]');
      assert.equal(buttons[0].getAttribute('data-action-key'), key2);
      assert.equal(buttons[1].getAttribute('data-action-key'), key1);
    });
  });
});

