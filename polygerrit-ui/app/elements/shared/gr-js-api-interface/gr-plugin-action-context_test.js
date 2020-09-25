/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-js-api-interface.js';
import {GrPluginActionContext} from './gr-plugin-action-context.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-plugin-action-context tests', () => {
  let instance;

  let plugin;

  setup(() => {
    pluginApi.install(p => { plugin = p; }, '0.1',
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
    await flush();
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

    test('click', () => {
      MockInteractions.tap(button);
      flush();
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
    document.addEventListener('show-alert', errorStub);
    instance.call();
    await flush();
    assert.isTrue(errorStub.calledOnce);
    assert.equal(errorStub.args[0][0].detail.message,
        'Plugin network error: Error: boom');
  });
});

