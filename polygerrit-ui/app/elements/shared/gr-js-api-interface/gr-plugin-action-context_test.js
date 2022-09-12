/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {GrPluginActionContext} from './gr-plugin-action-context';
import {addListenerForTest, waitEventLoop} from '../../../test/test-utils';
import {EventType} from '../../../types/events';
import {assert} from '@open-wc/testing';

suite('gr-plugin-action-context tests', () => {
  let instance;

  let plugin;

  setup(() => {
    window.Gerrit.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    instance = new GrPluginActionContext(plugin);
  });

  test('popup() and hide()', async () => {
    const popupApiStub = {
      _getElement: sinon.stub().returns(document.createElement('div')),
      close: sinon.stub(),
    };
    sinon.stub(plugin, 'popup').returns(Promise.resolve(popupApiStub));
    const el = document.createElement('span');
    instance.popup(el);
    await waitEventLoop();
    assert.isTrue(popupApiStub._getElement.called);
    instance.hide();
    assert.isTrue(popupApiStub.close.called);
  });

  test('textfield', () => {
    assert.equal(instance.textfield().tagName, 'PAPER-INPUT');
  });

  test('br', () => {
    assert.equal(instance.br().tagName, 'BR');
  });

  test('msg', () => {
    const el = instance.msg('foobar');
    assert.equal(el.tagName, 'GR-LABEL');
    assert.equal(el.textContent, 'foobar');
  });

  test('div', () => {
    const el1 = document.createElement('span');
    el1.textContent = 'foo';
    const el2 = document.createElement('div');
    el2.textContent = 'bar';
    const div = instance.div(el1, el2);
    assert.equal(div.tagName, 'DIV');
    assert.equal(div.textContent, 'foobar');
  });

  suite('button', () => {
    let clickStub;
    let button;
    setup(() => {
      clickStub = sinon.stub();
      button = instance.button('foo', {onclick: clickStub});
      // If you don't attach a Polymer element to the DOM, then the ready()
      // callback will not be called and then e.g. this.$ is undefined.
      document.body.appendChild(button);
    });

    test('click', async () => {
      button.click();
      await waitEventLoop();
      assert.isTrue(clickStub.called);
      assert.equal(button.textContent, 'foo');
    });

    teardown(() => {
      button.remove();
    });
  });

  test('checkbox', () => {
    const el = instance.checkbox();
    assert.equal(el.tagName, 'INPUT');
    assert.equal(el.type, 'checkbox');
  });

  test('label', () => {
    const fakeMsg = {};
    const fakeCheckbox = {};
    sinon.stub(instance, 'div');
    sinon.stub(instance, 'msg').returns(fakeMsg);
    instance.label(fakeCheckbox, 'foo');
    assert.isTrue(instance.div.calledWithExactly(fakeCheckbox, fakeMsg));
  });

  test('call', () => {
    instance.action = {
      method: 'METHOD',
      __key: 'key',
      __url: '/changes/1/revisions/2/foo~bar',
    };
    const sendStub = sinon.stub().returns(Promise.resolve());
    sinon.stub(plugin, 'restApi').returns({
      send: sendStub,
    });
    const payload = {foo: 'foo'};
    const successStub = sinon.stub();
    instance.call(payload, successStub);
    assert.isTrue(sendStub.calledWith(
        'METHOD', '/changes/1/revisions/2/foo~bar', payload));
  });

  test('call error', async () => {
    instance.action = {
      method: 'METHOD',
      __key: 'key',
      __url: '/changes/1/revisions/2/foo~bar',
    };
    const sendStub = sinon.stub().returns(Promise.reject(new Error('boom')));
    sinon.stub(plugin, 'restApi').returns({
      send: sendStub,
    });
    const errorStub = sinon.stub();
    addListenerForTest(document, EventType.SHOW_ALERT, errorStub);
    instance.call();
    await waitEventLoop();
    assert.isTrue(errorStub.calledOnce);
    assert.equal(errorStub.args[0][0].detail.message,
        'Plugin network error: Error: boom');
  });
});

