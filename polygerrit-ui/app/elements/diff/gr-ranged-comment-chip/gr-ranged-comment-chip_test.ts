/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {CommentRange} from '../../../types/common';
import {GrRangedCommentChip} from './gr-ranged-comment-chip';

suite('gr-ranged-comment-chip tests', () => {
  let element: GrRangedCommentChip;

  setup(() => {
    element = fixtureFromElement('gr-ranged-comment-chip').instantiate();
  });

  test('shows line range', async () => {
    element.range = {
      start_line: 2,
      start_character: 1,
      end_line: 5,
      end_character: 3,
    } as CommentRange;
    await flush();
    const textDiv = element.root!.querySelector<HTMLDivElement>('.row');
    assert.equal(textDiv!.innerText.trim(), 'Long comment range 2 - 5');
  });
});
