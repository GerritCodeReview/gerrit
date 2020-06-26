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
<meta charset="utf-8">
<title>gr-storage</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>

<test-fixture id="basic">
  <template>
    <gr-tooltip-content>
    </gr-tooltip-content>
  </template>
</test-fixture>

<script type="module">
import '../../../test/common-test-setup.js';
import './gr-tooltip-content.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
suite('gr-tooltip-content tests', () => {
  let element;
  setup(() => {
    element = fixture('basic');
  });

  test('icon is not visible by default', () => {
    assert.equal(dom(element.root)
        .querySelector('iron-icon').hidden, true);
  });

  test('position-below attribute is reflected', () => {
    assert.isFalse(element.hasAttribute('position-below'));
    element.positionBelow = true;
    assert.isTrue(element.hasAttribute('position-below'));
  });

  test('icon is visible with showIcon property', () => {
    element.showIcon = true;
    assert.equal(dom(element.root)
        .querySelector('iron-icon').hidden, false);
  });
});
</script>
