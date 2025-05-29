/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn';
import {assert, fixture, html} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {AttributeHelperPluginApi} from '../../../api/attribute-helper';

// Attribute helper only works on Polymer notify events, so we cannot use a Lit
// element for the test.
Polymer({
  is: 'foo-bar',
  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});

declare global {
  interface HTMLElementTagNameMap {
    'foo-bar': HTMLElement;
  }
}

suite('gr-attribute-helper tests', () => {
  let element: HTMLElement & {fooBar?: string};
  let instance: AttributeHelperPluginApi;

  setup(async () => {
    let plugin: PluginApi;
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    element = await fixture(html`<foo-bar></foo-bar>`);
    instance = plugin!.attributeHelper(element);
  });

  test('get resolves on value change from undefined', async () => {
    const fooBarWatch = instance.get('fooBar');
    element.fooBar = 'foo! bar!';
    const value = await fooBarWatch;

    assert.equal(value, 'foo! bar!');
  });

  test('get resolves to current attribute value', async () => {
    element.fooBar = 'foo-foo-bar';
    const fooBarWatch = instance.get('fooBar');
    element.fooBar = 'no bar';
    const value = await fooBarWatch;

    assert.equal(value, 'foo-foo-bar');
  });

  test('bind', () => {
    const stub = sinon.stub();
    element.fooBar = 'bar foo';
    const unbind = instance.bind('fooBar', stub);
    element.fooBar = 'partridge in a foo tree';
    element.fooBar = 'five gold bars';

    assert.equal(stub.callCount, 3);
    assert.deepEqual(stub.args[0], ['bar foo']);
    assert.deepEqual(stub.args[1], ['partridge in a foo tree']);
    assert.deepEqual(stub.args[2], ['five gold bars']);

    stub.reset();
    unbind();
    element.fooBar = 'ladies dancing';

    assert.isFalse(stub.called);
  });
});
