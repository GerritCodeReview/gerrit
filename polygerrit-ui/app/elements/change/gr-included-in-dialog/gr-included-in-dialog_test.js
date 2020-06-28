<!DOCTYPE html>
<!--
@license
Copyright (C) 2018 The Android Open Source Project

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
<title>gr-included-in-dialog</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>

<test-fixture id="basic">
  <template>
    <gr-included-in-dialog></gr-included-in-dialog>
  </template>
</test-fixture>

<script type="module">
import '../../../test/common-test-setup.js';
import './gr-included-in-dialog.js';
suite('gr-included-in-dialog', () => {
  let element;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
  });

  teardown(() => { sandbox.restore(); });

  test('_computeGroups', () => {
    const includedIn = {branches: [], tags: []};
    let filterText = '';
    assert.deepEqual(element._computeGroups(includedIn, filterText), []);

    includedIn.branches.push('master', 'development', 'stable-2.0');
    includedIn.tags.push('v1.9', 'v2.0', 'v2.1');
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    includedIn.external = {};
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
    ]);

    includedIn.external.foo = ['abc', 'def', 'ghi'];
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Branches', items: ['master', 'development', 'stable-2.0']},
      {title: 'Tags', items: ['v1.9', 'v2.0', 'v2.1']},
      {title: 'foo', items: ['abc', 'def', 'ghi']},
    ]);

    filterText = 'v2';
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);

    // Filtering is case-insensitive.
    filterText = 'V2';
    assert.deepEqual(element._computeGroups(includedIn, filterText), [
      {title: 'Tags', items: ['v2.0', 'v2.1']},
    ]);
  });

  test('_computeGroups with .bindValue', done => {
    element.$.filterInput.bindValue = 'stable-3.2';
    const includedIn = {branches: [], tags: []};
    includedIn.branches.push('master', 'stable-3.2');

    setTimeout(() => {
      const filterText = element._filterText;
      assert.deepEqual(element._computeGroups(includedIn, filterText), [
        {title: 'Branches', items: ['stable-3.2']},
      ]);

      done();
    });
  });
});
</script>
