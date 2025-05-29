/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-endpoint-decorator';
import '../gr-endpoint-param/gr-endpoint-param';
import '../gr-endpoint-slot/gr-endpoint-slot';
import {assert, fixture, html} from '@open-wc/testing';
import {mockPromise, queryAndAssert} from '../../../test/test-utils';
import {GrEndpointDecorator} from './gr-endpoint-decorator';
import {PluginApi} from '../../../api/plugin';
import {GrEndpointParam} from '../gr-endpoint-param/gr-endpoint-param';

suite('gr-endpoint-decorator', () => {
  let container: HTMLElement;

  let plugin: PluginApi;
  let decorationHook: any;
  let decorationHookWithSlot: any;
  let replacementHook: any;
  let first: GrEndpointDecorator;
  let second: GrEndpointDecorator;
  let banana: GrEndpointDecorator;

  setup(async () => {
    container = await fixture(
      html`<div>
        <gr-endpoint-decorator name="first">
          <gr-endpoint-param
            name="first-param"
            .value=${'barbar'}
          ></gr-endpoint-param>
          <p>
            <span>test slot</span>
            <gr-endpoint-slot name="test"></gr-endpoint-slot>
          </p>
        </gr-endpoint-decorator>
        <gr-endpoint-decorator name="second">
          <gr-endpoint-param
            name="second-param"
            .value=${'foofoo'}
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
        <gr-endpoint-decorator name="banana">
          <gr-endpoint-param
            name="banana-param"
            .value=${'yes'}
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>`
    );
    first = queryAndAssert<GrEndpointDecorator>(
      container,
      'gr-endpoint-decorator[name="first"]'
    );
    second = queryAndAssert<GrEndpointDecorator>(
      container,
      'gr-endpoint-decorator[name="second"]'
    );
    banana = queryAndAssert<GrEndpointDecorator>(
      container,
      'gr-endpoint-decorator[name="banana"]'
    );

    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://some/plugin/url.js'
    );
    // Decoration
    decorationHook = plugin.registerCustomComponent('first', 'some-module');
    const decorationHookPromise = mockPromise();
    decorationHook.onAttached(() => decorationHookPromise.resolve());

    // Decoration with slot
    decorationHookWithSlot = plugin.registerCustomComponent(
      'first',
      'some-module-2',
      {slot: 'test'}
    );
    const decorationHookSlotPromise = mockPromise();
    decorationHookWithSlot.onAttached(() =>
      decorationHookSlotPromise.resolve()
    );

    // Replacement
    replacementHook = plugin.registerCustomComponent('second', 'other-module', {
      replace: true,
    });
    const replacementHookPromise = mockPromise();
    replacementHook.onAttached(() => replacementHookPromise.resolve());

    await decorationHookPromise;
    await decorationHookSlotPromise;
    await replacementHookPromise;
  });

  test('imports plugin-provided modules into endpoints', () => {
    const endpoints = Array.from(
      container.querySelectorAll('gr-endpoint-decorator')
    );
    assert.equal(endpoints.length, 3);
  });

  test('first decoration', () => {
    const element = first;
    const modules = Array.from(element.children).filter(
      element => element.nodeName === 'SOME-MODULE'
    );
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal((module as any)['first-param'], 'barbar');
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
    const element = first;
    const modules = [...element.querySelectorAll('some-module-2')];
    assert.equal(modules.length, 1);
    const [module] = modules;
    assert.isOk(module);
    assert.equal((module as any)['first-param'], 'barbar');
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
    const element = second;
    const module = Array.from(element.children).find(
      element => element.nodeName === 'OTHER-MODULE'
    );
    assert.isOk(module);
    assert.equal((module as any)['second-param'], 'foofoo');
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
    const bananaHook = plugin.registerCustomComponent('banana', 'noob-noob');
    const bananaHookPromise = mockPromise();
    bananaHook.onAttached(() => bananaHookPromise.resolve());
    await bananaHookPromise;

    const element = banana;
    const module = Array.from(element.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.isOk(module);
  });

  test('two modules', async () => {
    const bananaHook1 = plugin.registerCustomComponent('banana', 'mod-one');
    const bananaHookPromise1 = mockPromise();
    bananaHook1.onAttached(() => bananaHookPromise1.resolve());
    await bananaHookPromise1;

    const bananaHook = plugin.registerCustomComponent('banana', 'mod-two');
    const bananaHookPromise2 = mockPromise();
    bananaHook.onAttached(() => bananaHookPromise2.resolve());
    await bananaHookPromise2;

    const element = banana;
    const module1 = Array.from(element.children).find(
      element => element.nodeName === 'MOD-ONE'
    );
    assert.isOk(module1);
    const module2 = Array.from(element.children).find(
      element => element.nodeName === 'MOD-TWO'
    );
    assert.isOk(module2);
  });

  test('late param setup', async () => {
    let element = banana;
    const param = queryAndAssert<GrEndpointParam>(element, 'gr-endpoint-param');
    param['value'] = undefined;
    await param.updateComplete;

    const bananaHook = plugin.registerCustomComponent('banana', 'noob-noob');
    const bananaHookPromise = mockPromise();
    bananaHook.onAttached(() => bananaHookPromise.resolve());

    element = queryAndAssert<GrEndpointDecorator>(
      container,
      'gr-endpoint-decorator[name="banana"]'
    );
    let module = Array.from(element.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    // Module waits for param to be defined.
    assert.isNotOk(module);
    const value = {abc: 'def'};
    param.value = value;
    await param.updateComplete;
    await bananaHookPromise;

    module = Array.from(element.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.isOk(module);
    assert.strictEqual((module as any)['banana-param'], value);
  });

  test('param is bound', async () => {
    const element = banana;
    const param = queryAndAssert<GrEndpointParam>(element, 'gr-endpoint-param');
    const value1 = {abc: 'def'};
    const value2 = {def: 'abc'};
    param.value = value1;
    await param.updateComplete;

    const bananaHook = plugin.registerCustomComponent('banana', 'noob-noob');
    const bananaHookPromise = mockPromise();
    bananaHook.onAttached(() => bananaHookPromise.resolve());
    await bananaHookPromise;

    const module = Array.from(element.children).find(
      element => element.nodeName === 'NOOB-NOOB'
    );
    assert.isOk(module);
    assert.strictEqual((module as any)['banana-param'], value1);

    param.value = value2;
    await param.updateComplete;
    assert.strictEqual((module as any)['banana-param'], value2);
  });
});
