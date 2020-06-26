<!DOCTYPE html>
<!--
@license
Copyright (C) 2016 The Android Open Source Project

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
<meta charset="utf-8">
<title>gr-account-label</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>

<test-fixture id="basic">
  <template>
    <gr-account-label></gr-account-label>
  </template>
</test-fixture>

<script type="module">
import '../../../test/common-test-setup.js';
import './gr-account-label.js';
suite('gr-account-label tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      getLoggedIn() { return Promise.resolve(false); },
    });
    element = fixture('basic');
    element._config = {
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
    };
  });

  test('null guard', () => {
    assert.doesNotThrow(() => {
      element.account = null;
    });
  });

  suite('_computeName', () => {
    test('not showing anonymous', () => {
      const account = {name: 'Wyatt'};
      assert.deepEqual(element._computeName(account, null), 'Wyatt');
    });

    test('showing anonymous but no config', () => {
      const account = {};
      assert.deepEqual(element._computeName(account, null),
          'Anonymous');
    });

    test('test for Anonymous Coward user and replace with Anonymous', () => {
      const config = {
        user: {
          anonymous_coward_name: 'Anonymous Coward',
        },
      };
      const account = {};
      assert.deepEqual(element._computeName(account, config),
          'Anonymous');
    });

    test('test for anonymous_coward_name', () => {
      const config = {
        user: {
          anonymous_coward_name: 'TestAnon',
        },
      };
      const account = {};
      assert.deepEqual(element._computeName(account, config),
          'TestAnon');
    });
  });
});
</script>
