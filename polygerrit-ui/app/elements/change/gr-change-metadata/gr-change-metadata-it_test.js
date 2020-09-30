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

import '../../../test/common-test-setup-karma.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import './gr-change-metadata.js';
import {resetPlugins} from '../../../test/test-utils.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';

const testHtmlPlugin = document.createElement('dom-module');
testHtmlPlugin.innerHTML = `
    <template>
      <style>
        html {
          --change-metadata-assignee: {
            display: none;
          }
          --change-metadata-label-status: {
            display: none;
          }
          --change-metadata-strategy: {
            display: none;
          }
          --change-metadata-topic: {
            display: none;
          }
        }
      </style>
    </template>
  `;
testHtmlPlugin.register('my-plugin-style');

const basicFixture = fixtureFromTemplate(
    html`<gr-change-metadata mutable="true"></gr-change-metadata>`
);

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-change-metadata integration tests', () => {
  let element;

  const sectionSelectors = [
    'section.strategy',
    'section.topic',
  ];

  const labels = {
    CI: {
      all: [
        {value: 1, name: 'user 2', _account_id: 1},
        {value: 2, name: 'user '},
      ],
      values: {
        ' 0': 'Don\'t submit as-is',
        '+1': 'No score',
        '+2': 'Looks good to me',
      },
    },
  };

  const getStyle = function(selector, name) {
    return window.getComputedStyle(
        element.root.querySelector(selector))[name];
  };

  function createElement() {
    const element = basicFixture.instantiate();
    element.change = {labels, status: 'NEW'};
    element.revision = {};
    return element;
  }

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      getLoggedIn() { return Promise.resolve(false); },
      deleteVote() { return Promise.resolve({ok: true}); },
    });
  });

  teardown(() => {
    resetPlugins();
  });

  suite('by default', () => {
    setup(done => {
      element = createElement();
      flush(done);
    });

    for (const sectionSelector of sectionSelectors) {
      test(sectionSelector + ' does not have display: none', () => {
        assert.notEqual(getStyle(sectionSelector, 'display'), 'none');
      });
    }
  });

  suite('with plugin style', () => {
    setup(done => {
      resetPlugins();
      pluginApi.install(plugin => {
        plugin.registerStyleModule('change-metadata', 'my-plugin-style');
      }, undefined, 'http://test.com/plugins/style.js');
      element = createElement();
      getPluginLoader().loadPlugins([]);
      getPluginLoader().awaitPluginsLoaded()
          .then(() => {
            flush(done);
          });
    });

    teardown(() => {
      document.body.querySelectorAll('custom-style')
          .forEach(style => { return style.remove(); });
    });

    for (const sectionSelector of sectionSelectors) {
      test('section.strategy may have display: none', () => {
        assert.equal(getStyle(sectionSelector, 'display'), 'none');
      });
    }
  });

  suite('label updates', () => {
    let plugin;

    setup(() => {
      pluginApi.install(p => {
        plugin = p;
        plugin.registerStyleModule('change-metadata', 'my-plugin-style');
      }, undefined, 'http://test.com/plugins/style.js');
      sinon.stub(getPluginLoader(), 'arePluginsLoaded').returns(true);
      getPluginLoader().loadPlugins([]);
      element = createElement();
    });

    teardown(() => {
      document.body.querySelectorAll('custom-style')
          .forEach(style => { return style.remove(); });
    });

    test('labels changed callback', done => {
      let callCount = 0;
      const labelChangeSpy = sinon.spy(arg => {
        callCount++;
        if (callCount === 1) {
          assert.deepEqual(arg, labels);
          assert.equal(arg.CI.all.length, 2);
          element.set(['change', 'labels'], {
            CI: {
              all: [
                {value: 1, name: 'user 2', _account_id: 1},
              ],
              values: {
                ' 0': 'Don\'t submit as-is',
                '+1': 'No score',
                '+2': 'Looks good to me',
              },
            },
          });
        } else if (callCount === 2) {
          assert.equal(arg.CI.all.length, 1);
          done();
        }
      });

      plugin.changeMetadata().onLabelsChanged(labelChangeSpy);
    });
  });
});
