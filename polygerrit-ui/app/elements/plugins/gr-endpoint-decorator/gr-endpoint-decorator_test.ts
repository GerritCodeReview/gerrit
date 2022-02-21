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

import '../../../test/common-test-setup-karma';
import './gr-endpoint-decorator';
import '../gr-endpoint-param/gr-endpoint-param';
import '../gr-endpoint-slot/gr-endpoint-slot';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {resetPlugins} from '../../../test/test-utils';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GrEndpointDecorator} from './gr-endpoint-decorator';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointParam} from '../gr-endpoint-param/gr-endpoint-param';

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
  let container: GrEndpointDecorator;

  let plugin: PluginApi;
  let decorationHook: any;
  let decorationHookWithSlot: any;
  let replacementHook: any;

  setup(async () => {
    resetPlugins();
    container = basicFixture.instantiate() as GrEndpointDecorator;
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://some/plugin/url.js'
    );
    // Decoration
    decorationHook = plugin.registerCustomComponent('first', 'some-module');
    decorationHookWithSlot = plugin.registerCustomComponent(
      'first',
      'some-module-2',
      {slot: 'test'}
    );
    // Replacement
    replacementHook = plugin.registerCustomComponent('second', 'other-module', {
      replace: true,
    });
    // Mimic all plugins loaded.
    getPluginLoader().loadPlugins([]);
    await flush();
  });

  teardown(() => {
    resetPlugins();
  });

  test('imports plugin-provided modules into endpoints', () => {
    const endpoints = Array.from(
      container.querySelectorAll('gr-endpoint-decorator')
    );
    assert.equal(endpoints.length, 3);
  });

  test('decoration', () => {
    const element = container.querySelector(
      'gr-endpoint-decorator[name="first"]'
    ) as GrEndpointDecorator;
    const modules = Array.from(element.root!.children).filter(
      element => element.nodeName === 'SOME-MODULE'
    );
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal((module as any)['someparam'], 'barbar');
    return decorationHook
      .getLastAttached()
      .then((element: any) => {
        assert.strictEqual(element, module);
      })
      .then(() => {
        element.remove();
        assert.equal(decorationHook.getAllAttached().length, 0);
      });
  });

  test('decoration with slot', () => {
    const element = container.querySelector(
      'gr-endpoint-decorator[name="first"]'
    ) as GrEndpointDecorator;
    const modules = [...element.querySelectorAll('some-module-2')];
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal((module as any)['someparam'], 'barbar');
    return decorationHookWithSlot
      .getLastAttached()
      .then((element: any) => {
        assert.strictEqual(element, module);
      })
      .then(() => {
        element.remove();
        assert.equal(decorationHookWithSlot.getAllAttached().length, 0);
      });
  });

  test('replacement', () => {
    const element = container.querySelector(
      'gr-endpoint-decorator[name="second"]'
    ) as GrEndpointDecorator;
    const module = Array.from(element.root!.children).find(
      element => element.nodeName === 'OTHER-MODULE'
    );
    assert.isOk(module);
    assert.equal((module as any)['someparam'], 'foofoo');
    return replacementHook
      .getLastAttached()
      .then((element: any) => {
        assert.strictEqual(element, module);
      })
      .then(() => {
        element.remove();
        assert.equal(replacementHook.getAllAttached().length, 0);
      });
  });

  test('late registration', async () => {
    plugin.registerCustomComponent('banana', 'noob-noob');
    await flush();
    const element = container.querySelector(
      'gr-endpoint-decorator[name="banana"]'
    ) as GrEndpointDecorator;
    const module = Array.from(element.root!.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.isOk(module);
  });

  test('two modules', async () => {
    plugin.registerCustomComponent('banana', 'mod-one');
    plugin.registerCustomComponent('banana', 'mod-two');
    await flush();
    const element = container.querySelector(
      'gr-endpoint-decorator[name="banana"]'
    ) as GrEndpointDecorator;
    const module1 = Array.from(element.root!.children).find(
      element => element.nodeName === 'MOD-ONE'
    );
    assert.isOk(module1);
    const module2 = Array.from(element.root!.children).find(
      element => element.nodeName === 'MOD-TWO'
    );
    assert.isOk(module2);
  });

  test('late param setup', async () => {
    const element = container.querySelector(
      'gr-endpoint-decorator[name="banana"]'
    ) as GrEndpointDecorator;
    const param = element.querySelector('gr-endpoint-param') as GrEndpointParam;
    param['value'] = undefined;
    plugin.registerCustomComponent('banana', 'noob-noob');
    await flush();
    let module = Array.from(element.root!.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    // Module waits for param to be defined.
    assert.isNotOk(module);
    const value = {abc: 'def'};
    param.value = value;

    await flush();
    module = Array.from(element.root!.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.isOk(module);
    assert.strictEqual((module as any)['someParam'], value);
  });

  test('param is bound', async () => {
    const element = container.querySelector(
      'gr-endpoint-decorator[name="banana"]'
    ) as GrEndpointDecorator;
    const param = element.querySelector('gr-endpoint-param') as GrEndpointParam;
    const value1 = {abc: 'def'};
    const value2 = {def: 'abc'};
    param.value = value1;
    plugin.registerCustomComponent('banana', 'noob-noob');
    await flush();
    const module = Array.from(element.root!.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.strictEqual((module as any)['someParam'], value1);
    param.value = value2;
    assert.strictEqual((module as any)['someParam'], value2);
  });
});
