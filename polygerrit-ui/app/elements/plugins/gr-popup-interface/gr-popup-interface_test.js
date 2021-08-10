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
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {GrPopupInterface} from './gr-popup-interface.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';

class GrUserTestPopupElement extends PolymerElement {
  static get is() { return 'gr-user-test-popup'; }

  static get template() {
    return html`<div id="barfoo">some test module</div>`;
  }
}

customElements.define(GrUserTestPopupElement.is, GrUserTestPopupElement);

const containerFixture = fixtureFromElement('div');

const pluginApi = _testOnly_initGerritPluginApi();
suite('gr-popup-interface tests', () => {
  let container;
  let instance;
  let plugin;

  setup(() => {
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    container = containerFixture.instantiate();
    sinon.stub(plugin, 'hook').returns({
      getLastAttached() {
        return Promise.resolve(container);
      },
    });
  });

  suite('manual', () => {
    setup(() => {
      instance = new GrPopupInterface(plugin);
    });

    test('open', async () => {
      const api = await instance.open();
      assert.strictEqual(api, instance);
      const manual = document.createElement('div');
      manual.id = 'foobar';
      manual.innerHTML = 'manual content';
      api._getElement().appendChild(manual);
      await flush();
      assert.equal(
          container.querySelector('#foobar').textContent, 'manual content');
    });

    test('close', async () => {
      const api = await instance.open();
      assert.isTrue(api._getElement().node.opened);
      api.close();
      assert.isFalse(api._getElement().node.opened);
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
      const api = await instance.open();
      assert.isTrue(api._getElement().node.opened);
      api.close();
      assert.isFalse(api._getElement().node.opened);
    });
  });
});
//
