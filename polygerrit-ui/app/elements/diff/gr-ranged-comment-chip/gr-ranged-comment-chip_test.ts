/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import {GrRangedCommentChip} from './gr-ranged-comment-chip';

const basicFixture = fixtureFromElement('gr-ranged-comment-chip');

suite('gr-ranged-comment-chip tests', () => {
  let element: GrRangedCommentChip;

  setup(done => {
    element = basicFixture.instantiate();
    flush(done);
  });

  test('shows line range', done => {
    element.range = {
      start_character: 1,
      start_line: 2,
      end_character: 3,
      end_line: 4,
    };
    flush(() => {
      const textDiv = element.shadowRoot?.querySelector<HTMLDivElement>(
        '.chip'
      );
      assert.equal(textDiv?.innerText, 'Long comment range 2 - 4');
      done();
    });
  });
});
