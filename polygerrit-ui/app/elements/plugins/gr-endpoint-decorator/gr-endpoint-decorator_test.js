/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import './gr-endpoint-decorator.js';
import '../gr-endpoint-param/gr-endpoint-param.js';
import '../gr-endpoint-slot/gr-endpoint-slot.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {resetPlugins} from '../../../test/test-utils.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

const basicFixture = fixtureFromTemplate(
    html`<div>
  <gr-endpoint-decorator name="first">
    <gr-endpoint-param name="someparam" value="barbar"></gr-endpoint-param>
    <p>
      <span>test slot</span>
      <gr-endpoint-slot name="test"></gr-endpoint-slot>
    </p>
  </gr-endpoint-decorator>
  <gr-endpoint-decorator name="second">
    <gr-endpoint-param name="someparam" value="foofoo"></gr-endpoint-param>
  </gr-endpoint-decorator>
  <gr-endpoint-decorator name="banana">
    <gr-endpoint-param name="someParam" value="yes"></gr-endpoint-param>
  </gr-endpoint-decorator>
</div>`
);

suite('gr-endpoint-decorator', () => {
  let container;

  let plugin;
  let decorationHook;
  let decorationHookWithSlot;
  let replacementHook;

  setup(done => {
    resetPlugins();
    container = basicFixture.instantiate();
    sinon.stub(getPluginEndpoints(), 'importUrl')
        .callsFake( url => Promise.resolve());
    pluginApi.install(p => plugin = p, '0.1',
        'http://some/plugin/url.html');
    // Decoration
    decorationHook = plugin.registerCustomComponent('first', 'some-module');
    decorationHookWithSlot = plugin.registerCustomComponent(
        'first',
        'some-module-2',
        {slot: 'test'}
    );
    // Replacement
    replacementHook = plugin.registerCustomComponent(
        'second', 'other-module', {replace: true});
    // Mimic all plugins loaded.
    pluginLoader.loadPlugins([]);
    flush(done);
  });

  teardown(() => {
    resetPlugins();
  });

  test('imports plugin-provided modules into endpoints', () => {
    const endpoints =
        Array.from(container.querySelectorAll('gr-endpoint-decorator'));
    assert.equal(endpoints.length, 3);
    assert.isTrue(getPluginEndpoints().importUrl.calledWith(
        new URL('http://some/plugin/url.html')
    ));
  });

  test('decoration', () => {
    const element =
        container.querySelector('gr-endpoint-decorator[name="first"]');
    const modules = Array.from(dom(element.root).children).filter(
        element => element.nodeName === 'SOME-MODULE');
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal(module['someparam'], 'barbar');
    return decorationHook.getLastAttached()
        .then(element => {
          assert.strictEqual(element, module);
        })
        .then(() => {
          element.remove();
          assert.equal(decorationHook.getAllAttached().length, 0);
        });
  });

  test('decoration with slot', () => {
    const element =
        container.querySelector('gr-endpoint-decorator[name="first"]');
    const modules = [...dom(element).querySelectorAll('some-module-2')];
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal(module['someparam'], 'barbar');
    return decorationHookWithSlot.getLastAttached()
        .then(element => {
          assert.strictEqual(element, module);
        })
        .then(() => {
          element.remove();
          assert.equal(decorationHookWithSlot.getAllAttached().length, 0);
        });
  });

  test('replacement', () => {
    const element =
        container.querySelector('gr-endpoint-decorator[name="second"]');
    const module = Array.from(dom(element.root).children).find(
        element => element.nodeName === 'OTHER-MODULE');
    assert.isOk(module);
    assert.equal(module['someparam'], 'foofoo');
    return replacementHook.getLastAttached()
        .then(element => {
          assert.strictEqual(element, module);
        })
        .then(() => {
          element.remove();
          assert.equal(replacementHook.getAllAttached().length, 0);
        });
  });

  test('late registration', done => {
    plugin.registerCustomComponent('banana', 'noob-noob');
    flush(() => {
      const element =
          container.querySelector('gr-endpoint-decorator[name="banana"]');
      const module = Array.from(dom(element.root).children).find(
          element => element.nodeName === 'NOOB-NOOB');
      assert.isOk(module);
      done();
    });
  });

  test('two modules', done => {
    plugin.registerCustomComponent('banana', 'mod-one');
    plugin.registerCustomComponent('banana', 'mod-two');
    flush(() => {
      const element =
          container.querySelector('gr-endpoint-decorator[name="banana"]');
      const module1 = Array.from(dom(element.root).children).find(
          element => element.nodeName === 'MOD-ONE');
      assert.isOk(module1);
      const module2 = Array.from(dom(element.root).children).find(
          element => element.nodeName === 'MOD-TWO');
      assert.isOk(module2);
      done();
    });
  });

  test('late param setup', done => {
    const element =
        container.querySelector('gr-endpoint-decorator[name="banana"]');
    const param = dom(element).querySelector('gr-endpoint-param');
    param['value'] = undefined;
    plugin.registerCustomComponent('banana', 'noob-noob');
    flush(() => {
      let module = Array.from(dom(element.root).children).find(
          element => element.nodeName === 'NOOB-NOOB');
      // Module waits for param to be defined.
      assert.isNotOk(module);
      const value = {abc: 'def'};
      param.value = value;
      flush(() => {
        module = Array.from(dom(element.root).children).find(
            element => element.nodeName === 'NOOB-NOOB');
        assert.isOk(module);
        assert.strictEqual(module['someParam'], value);
        done();
      });
    });
  });

  test('param is bound', done => {
    const element =
        container.querySelector('gr-endpoint-decorator[name="banana"]');
    const param = dom(element).querySelector('gr-endpoint-param');
    const value1 = {abc: 'def'};
    const value2 = {def: 'abc'};
    param.value = value1;
    plugin.registerCustomComponent('banana', 'noob-noob');
    flush(() => {
      const module = Array.from(dom(element.root).children).find(
          element => element.nodeName === 'NOOB-NOOB');
      assert.strictEqual(module['someParam'], value1);
      param.value = value2;
      assert.strictEqual(module['someParam'], value2);
      done();
    });
  });
});
