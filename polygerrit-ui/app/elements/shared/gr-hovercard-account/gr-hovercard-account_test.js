<!DOCTYPE html>
<!--
@license
Copyright (C) 2020 The Android Open Source Project

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

<meta name="viewport"
      content="width=device-width, minimum-scale=1.0, initial-scale=1.0, user-scalable=yes">
<title>gr-hovercard-account</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>
<script src="../../../node_modules/iron-test-helpers/mock-interactions.js" type="module"></script>

<test-fixture id="basic">
  <template>
    <gr-hovercard-account class="hovered"></gr-hovercard-account>
  </template>
</test-fixture>


<script type="module">
  import '../../../test/common-test-setup.js';
  import './gr-hovercard-account.js';

  suite('gr-hovercard-account tests', () => {
    let element;
    const ACCOUNT = {
      email: 'kermit@gmail.com',
      username: 'kermit',
      name: 'Kermit The Frog',
      _account_id: '31415926535',
    };

    setup(() => {
      element = fixture('basic');
      element.account = Object.assign({}, ACCOUNT);
      element.show({});
      flushAsynchronousOperations();
    });

    test('account name is shown', () => {
      assert.equal(element.shadowRoot.querySelector('.name').innerText,
          'Kermit The Frog');
    });

    test('account status is not shown if the property is not set', () => {
      assert.isNull(element.shadowRoot.querySelector('.status'));
    });

    test('account status is displayed', () => {
      element.account = Object.assign({status: 'OOO'}, ACCOUNT);
      flushAsynchronousOperations();
      assert.equal(element.shadowRoot.querySelector('.status .value').innerText,
          'OOO');
    });

    test('voteable div is not shown if the property is not set', () => {
      assert.isNull(element.shadowRoot.querySelector('.voteable'));
    });

    test('voteable div is displayed', () => {
      element.voteableText = 'CodeReview: +2';
      flushAsynchronousOperations();
      assert.equal(element.shadowRoot.querySelector('.voteable .value').innerText,
          element.voteableText);
    });
  });
</script>
