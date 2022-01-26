/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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
import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import './gr-change-list-column-requirement';
import {GrChangeListColumnRequirement} from './gr-change-list-column-requirement';
import {
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
  createNonApplicableSubmitRequirementResultInfo,
  createChange,
} from '../../../test/test-data-generators';
import {ChangeInfo, SubmitRequirementResultInfo} from '../../../api/rest-api';
import {StandardLabels} from '../../../utils/label-util';

suite('gr-change-list-column-requirement tests', () => {
  let element: GrChangeListColumnRequirement;
  let change: ChangeInfo;
  setup(() => {
    const submitRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: StandardLabels.CODE_REVIEW,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Verified=MAX -label:Verified=MIN',
      },
    };
    change = {
      ...createChange(),
      submit_requirements: [
        submitRequirement,
        createNonApplicableSubmitRequirementResultInfo(),
      ],
      unresolved_comment_count: 1,
    };
  });

  test('renders', async () => {
    element = await fixture<GrChangeListColumnRequirement>(
      html`<gr-change-list-column-requirement
        .change=${change}
        .labelName=${StandardLabels.CODE_REVIEW}
      >
      </gr-change-list-column-requirement>`
    );
    expect(element).shadowDom.to.equal(`<div class="container">
      <iron-icon
        class="check-circle-filled"
        icon="gr-icons:check-circle-filled"
      >
      </iron-icon>
    </div>`);
  });
});
