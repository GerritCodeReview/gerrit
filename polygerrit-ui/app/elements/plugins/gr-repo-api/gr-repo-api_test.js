<!DOCTYPE html>
<!--
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<meta name="viewport" content="width=device-width, minimum-scale=1.0, initial-scale=1.0, user-scalable=yes">
<title>gr-repo-api</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>

<test-fixture id="basic">
  <template>
    <gr-endpoint-decorator name="repo-command">
    </gr-endpoint-decorator>
  </template>
</test-fixture>

<script type="module">
import '../../../test/common-test-setup.js';
import '../gr-endpoint-decorator/gr-endpoint-decorator.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';

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
    const element = fixture('basic');
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
</script>
