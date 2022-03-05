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

import '../../../test/common-test-setup-karma';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import {GrPopupInterface} from './gr-popup-interface';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';
import {queryAndAssert} from '../../../test/test-utils';

class GrUserTestPopupElement extends PolymerElement {
  static get is() {
    return 'gr-user-test-popup';
  }

  static get template() {
    return html`<div id="barfoo">some test module</div>`;
  }
}

customElements.define(GrUserTestPopupElement.is, GrUserTestPopupElement);

const containerFixture = fixtureFromElement('div');

suite('gr-popup-interface tests', () => {
  let container: HTMLElement;
  let instance: GrPopupInterface;
  let plugin: PluginApi;

  setup(() => {
    window.Gerrit.install(
      p => {
        plugin = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    container = containerFixture.instantiate();
    sinon.stub(plugin, 'hook').returns({
      getLastAttached() {
        return Promise.resolve(container);
      },
    } as HookApi<PluginElement>);
  });

  suite('manual', () => {
    setup(() => {
      instance = new GrPopupInterface(plugin);
    });

    test('open', async () => {
      const api = (await instance.open()) as GrPopupInterface;
      assert.strictEqual(api, instance);
      const manual = document.createElement('div');
      manual.id = 'foobar';
      manual.innerHTML = 'manual content';
      api._getElement().appendChild(manual);
      await flush();
      assert.equal(
        queryAndAssert(container, '#foobar').textContent,
        'manual content'
      );
    });

    test('close', async () => {
      const api = (await instance.open()) as GrPopupInterface;
      assert.isTrue((api._getElement() as any).node.opened);
      api.close();
      assert.isFalse((api._getElement() as any).node.opened);
    });
  });

  suite('components', () => {
    setup(() => {
      instance = new GrPopupInterface(plugin, 'gr-user-test-popup');
    });

    test('open', async () => {
      await instance.open();
      assert.isNotNull(container.querySelector('gr-user-test-popup'));
    });

    test('close', async () => {
      const api = (await instance.open()) as GrPopupInterface;
      assert.isTrue((api._getElement() as any).node.opened);
      api.close();
      assert.isFalse((api._getElement() as any).node.opened);
    });
  });
});
