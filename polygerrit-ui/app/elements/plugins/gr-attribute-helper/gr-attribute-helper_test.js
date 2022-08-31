/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn';
// eslint-disable-next-line import/named
import {fixture, html, assert} from '@open-wc/testing';

Polymer({
  is: 'gr-attribute-helper-some-element',
  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});

suite('gr-attribute-helper tests', () => {
  let element;
  let instance;

  setup(async () => {
    let plugin;
    window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://test.com/plugins/testplugin/static/test.js'
    );
    element = await fixture(
        html`
        <gr-attribute-helper-some-element></gr-attribute-helper-some-element>
      `
    );
    instance = plugin.attributeHelper(element);
  });

  test('resolved on value change from undefined', () => {
    const promise = instance.get('fooBar').then(value => {
      assert.equal(value, 'foo! bar!');
    });
    element.fooBar = 'foo! bar!';
    return promise;
  });

  test('resolves to current attribute value', () => {
    element.fooBar = 'foo-foo-bar';
    const promise = instance.get('fooBar').then(value => {
      assert.equal(value, 'foo-foo-bar');
    });
    element.fooBar = 'no bar';
    return promise;
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
    instance.fooBar = 'ladies dancing';
    assert.isFalse(stub.called);
  });
});

