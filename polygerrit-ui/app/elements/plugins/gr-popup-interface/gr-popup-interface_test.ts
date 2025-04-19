/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import {GrPopupInterface} from './gr-popup-interface';
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';
import {queryAndAssert, waitEventLoop} from '../../../test/test-utils';
import {LitElement, html} from 'lit';
import {customElement} from 'lit/decorators.js';
import {fixture, assert} from '@open-wc/testing';

@customElement('gr-user-test-popup')
class GrUserTestPopupElement extends LitElement {
  override render() {
    return html`<div id="barfoo">some test module</div>`;
  }
}
declare global {
  interface HTMLElementTagNameMap {
    'gr-user-test-popup': GrUserTestPopupElement;
  }
}

suite('gr-popup-interface tests', () => {
  let container: HTMLElement;
  let instance: GrPopupInterface;
  let plugin: PluginApi;

  setup(async () => {
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    container = await fixture(html`<div></div>`);
    sinon.stub(plugin, 'hook').returns({
      getLastAttached() {
        return Promise.resolve(container);
      },
    } as HookApi<PluginElement>);
  });

  suite('manual', () => {
    setup(async () => {
      instance = new GrPopupInterface(plugin);
      await instance.open();
    });

    test('open', async () => {
      const manual = document.createElement('div');
      manual.id = 'foobar';
      manual.innerHTML = 'manual content';
      const popup = instance._getElement();
      assert.isOk(popup);
      popup.appendChild(manual);
      await waitEventLoop();
      assert.equal(
        queryAndAssert(container, '#foobar').textContent,
        'manual content'
      );
    });

    test('close', async () => {
      assert.isOk(instance._getElement());
      assert.isTrue(instance._getElement()!.opened);
      instance.close();
      assert.isOk(instance._getElement());
      assert.isFalse(instance._getElement()!.opened);
    });
  });

  suite('components', () => {
    setup(async () => {
      instance = new GrPopupInterface(plugin, 'gr-user-test-popup');
      await instance.open();
    });

    test('open', async () => {
      assert.isNotNull(container.querySelector('gr-user-test-popup'));
    });

    test('close', async () => {
      assert.isOk(instance._getElement());
      assert.isTrue(instance._getElement()!.opened);
      instance.close();
      assert.isOk(instance._getElement());
      assert.isFalse(instance._getElement()!.opened);
    });
  });
});
