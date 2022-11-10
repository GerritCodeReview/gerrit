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
import {PluginApi} from '../../../api/plugin';
import {SinonStub, stub, spy} from 'sinon';
import {PopupPluginApi} from '../../../api/popup';
import {GrButton} from '../gr-button/gr-button';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {ActionType} from '../../../api/change-actions';
import {HttpMethod} from '../../../api/rest-api';
import {RestPluginApi} from '../../../api/rest';

suite('gr-plugin-action-context tests', () => {
  let instance: GrPluginActionContext;

  let plugin: PluginApi;

  setup(() => {
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    instance = new GrPluginActionContext(
      plugin,
      {
        label: 'MyAction',
        method: HttpMethod.POST,
        __key: 'key',
        __url: '/changes/1/revisions/2/foo~bar',
        __type: ActionType.REVISION,
      },
      createChange(),
      createRevision()
    );
  });

  test('popup() and hide()', async () => {
    const popupApiStub = {
      _getElement: stub().returns(document.createElement('div')),
      close: stub(),
    } as PopupPluginApi & {_getElement: SinonStub; close: SinonStub};
    stub(plugin, 'popup').resolves(popupApiStub);
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
    let clickStub: SinonStub;
    let button: GrButton;
    setup(() => {
      clickStub = stub();
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
    const divSpy = spy(instance, 'div');
    const fakeMsg = document.createElement('gr-label');
    const fakeCheckbox = document.createElement('input');
    stub(instance, 'msg').returns(fakeMsg);

    instance.label(fakeCheckbox, 'foo');

    assert.isTrue(divSpy.calledWithExactly(fakeCheckbox, fakeMsg));
  });

  test('call', () => {
    const fakeRestApi = {
      send: stub().resolves(),
    } as RestPluginApi & {send: SinonStub};
    stub(plugin, 'restApi').returns(fakeRestApi);

    const payload = {foo: 'foo'};
    instance.call(payload, () => {});

    assert.isTrue(
      fakeRestApi.send.calledWith(
        HttpMethod.POST,
        '/changes/1/revisions/2/foo~bar',
        payload
      )
    );
  });

  test('call error', async () => {
    const fakeRestApi = {
      send: () => Promise.reject(new Error('boom')),
    } as unknown as RestPluginApi;
    stub(plugin, 'restApi').returns(fakeRestApi);
    const errorStub = stub();
    addListenerForTest(document, EventType.SHOW_ALERT, errorStub);

    instance.call({}, () => {});
    await waitEventLoop();

    assert.isTrue(errorStub.calledOnce);
    assert.equal(
      errorStub.args[0][0].detail.message,
      'Plugin network error: Error: boom'
    );
  });
});
