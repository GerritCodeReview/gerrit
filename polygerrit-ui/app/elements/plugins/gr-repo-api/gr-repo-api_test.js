
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









const basicFixture = fixtureFromTemplate(html`
<gr-endpoint-decorator name="repo-command">
    </gr-endpoint-decorator>
`);


import '../../../test/common-test-setup.js';
import '../gr-endpoint-decorator/gr-endpoint-decorator.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';


const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-repo-api tests', () => {
  let sandbox;
  let repoApi;

  setup(() => {
    sandbox = sinon.sandbox.create();
    let plugin;
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    pluginLoader.loadPlugins([]);
    repoApi = plugin.project();
  });

  teardown(() => {
    repoApi = null;
    sandbox.restore();
  });

  test('exists', () => {
    assert.isOk(repoApi);
  });

  test('works', done => {
    const attachedStub = sandbox.stub();
    const tapStub = sandbox.stub();
    repoApi
        .createCommand('foo', attachedStub)
        .onTap(tapStub);
    const element = basicFixture.instantiate();
    flush(() => {
      assert.isTrue(attachedStub.called);
      const pluginCommand = element.shadowRoot
          .querySelector('gr-plugin-repo-command');
      assert.isOk(pluginCommand);
      const btn = pluginCommand.shadowRoot
          .querySelector('gr-button');
      assert.isOk(btn);
      assert.equal(btn.textContent, 'foo');
      assert.isFalse(tapStub.called);
      MockInteractions.tap(btn);
      assert.isTrue(tapStub.called);
      done();
    });
  });
});

