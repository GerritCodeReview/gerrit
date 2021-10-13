/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../test/common-test-setup-karma';
import './gr-hovercard-run';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrHovercardRun} from './gr-hovercard-run';

const basicFixture = fixtureFromTemplate(html`
  <gr-hovercard-run class="hovered"></gr-hovercard-run>
`);

suite('gr-hovercard-run tests', () => {
  let element: GrHovercardRun;

  setup(async () => {
    element = basicFixture.instantiate() as GrHovercardRun;
    await flush();
  });

  teardown(() => {
    element.hide(new MouseEvent('click'));
  });

  test('hovercard is shown', () => {
    assert.equal(element.computeIcon(), '');
  });
});
