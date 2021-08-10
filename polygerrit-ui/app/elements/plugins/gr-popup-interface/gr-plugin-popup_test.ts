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

import '../../../test/common-test-setup-karma';
import './gr-plugin-popup';
import {GrPluginPopup} from './gr-plugin-popup';

const basicFixture = fixtureFromElement('gr-plugin-popup');

suite('gr-plugin-popup tests', () => {
  let element: GrPluginPopup;
  let overlayOpen: sinon.SinonStub;
  let overlayClose: sinon.SinonStub;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
    overlayOpen = stub('gr-overlay', 'open').callsFake(() => Promise.resolve());
    overlayClose = stub('gr-overlay', 'close');
  });

  test('exists', () => {
    assert.isOk(element);
  });

  test('open uses open() from gr-overlay', async () => {
    await element.open();
    assert.isTrue(overlayOpen.called);
  });

  test('close uses close() from gr-overlay', () => {
    element.close();
    assert.isTrue(overlayClose.called);
  });
});
