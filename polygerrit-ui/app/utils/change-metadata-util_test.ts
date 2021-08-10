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

import '../test/common-test-setup-karma';
import {
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../test/test-data-generators';
import {extractAssociatedLabels} from './change-metadata-util';

suite('change-metadata-util', () => {
  suite('extractAssociatedLabels()', () => {
    function createSubmitRequirementExpressionInfoWith(expression: string) {
      return {
        ...createSubmitRequirementResultInfo(),
        submittability_expression_result: {
          ...createSubmitRequirementExpressionInfo(),
          expression,
        },
      };
    }

    test('1 label', () => {
      const submitRequirement = createSubmitRequirementExpressionInfoWith(
        'label:Verified=MAX -label:Verified=MIN'
      );
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['Verified']);
    });
    test('label with number', () => {
      const submitRequirement = createSubmitRequirementExpressionInfoWith(
        'label2:verified=MAX'
      );
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['verified']);
    });
    test('2 labels', () => {
      const submitRequirement = createSubmitRequirementExpressionInfoWith(
        'label:Verified=MAX -label:Code-Review=MIN'
      );
      const labels = extractAssociatedLabels(submitRequirement);
      assert.deepEqual(labels, ['Verified', 'Code-Review']);
    });
  });
});
