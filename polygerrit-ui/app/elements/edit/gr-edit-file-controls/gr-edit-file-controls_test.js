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
<title>gr-edit-file-controls</title>

<script src="/node_modules/@webcomponents/webcomponentsjs/custom-elements-es5-adapter.js"></script>

<script src="/node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js"></script>
<script src="/components/wct-browser-legacy/browser.js"></script>

<test-fixture id="basic">
  <template>
    <gr-edit-file-controls></gr-edit-file-controls>
  </template>
</test-fixture>

<script type="module">
import '../../../test/common-test-setup.js';
import '../gr-edit-constants.js';
import './gr-edit-file-controls.js';
import {GrEditConstants} from '../gr-edit-constants.js';

suite('gr-edit-file-controls tests', () => {
  let element;
  let sandbox;
  let fileActionHandler;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
    fileActionHandler = sandbox.stub();
    element.addEventListener('file-action-tap', fileActionHandler);
  });

  teardown(() => { sandbox.restore(); });

  test('open tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flushAsynchronousOperations();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="open"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.OPEN.id, path: 'foo'});
  });

  test('delete tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flushAsynchronousOperations();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="delete"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.DELETE.id, path: 'foo'});
  });

  test('restore tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flushAsynchronousOperations();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="restore"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.RESTORE.id, path: 'foo'});
  });

  test('rename tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flushAsynchronousOperations();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="rename"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.RENAME.id, path: 'foo'});
  });

  test('computed properties', () => {
    assert.equal(element._allFileActions.length, 4);
  });
});
</script>
