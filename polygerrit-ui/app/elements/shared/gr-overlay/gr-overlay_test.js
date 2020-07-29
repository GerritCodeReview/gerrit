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
import './gr-overlay.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-overlay>
      <div>content</div>
    </gr-overlay>
`);

suite('gr-overlay tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('popstate listener is attached on open and removed on close', () => {
    const addEventListenerStub = sinon.stub(window, 'addEventListener');
    const removeEventListenerStub = sinon.stub(window, 'removeEventListener');
    element.open();
    assert.isTrue(addEventListenerStub.called);
    assert.equal(addEventListenerStub.lastCall.args[0], 'popstate');
    assert.equal(addEventListenerStub.lastCall.args[1],
        element._boundHandleClose);
    element._overlayClosed();
    assert.isTrue(removeEventListenerStub.called);
    assert.equal(removeEventListenerStub.lastCall.args[0], 'popstate');
    assert.equal(removeEventListenerStub.lastCall.args[1],
        element._boundHandleClose);
  });

  test('events are fired on fullscreen view', done => {
    sinon.stub(element, '_isMobile').returns(true);
    const openHandler = sinon.stub();
    const closeHandler = sinon.stub();
    element.addEventListener('fullscreen-overlay-opened', openHandler);
    element.addEventListener('fullscreen-overlay-closed', closeHandler);

    element.open().then(() => {
      assert.isTrue(element._isMobile.called);
      assert.isTrue(element._fullScreenOpen);
      assert.isTrue(openHandler.called);

      element._overlayClosed();
      assert.isFalse(element._fullScreenOpen);
      assert.isTrue(closeHandler.called);
      done();
    });
  });

  test('events are not fired on desktop view', done => {
    sinon.stub(element, '_isMobile').returns(false);
    const openHandler = sinon.stub();
    const closeHandler = sinon.stub();
    element.addEventListener('fullscreen-overlay-opened', openHandler);
    element.addEventListener('fullscreen-overlay-closed', closeHandler);

    element.open().then(() => {
      assert.isTrue(element._isMobile.called);
      assert.isFalse(element._fullScreenOpen);
      assert.isFalse(openHandler.called);

      element._overlayClosed();
      assert.isFalse(element._fullScreenOpen);
      assert.isFalse(closeHandler.called);
      done();
    });
  });
});

