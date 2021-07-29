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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-form-styles">
    :host {
      display: inline-block;
    }
    input {
      width: 20em;
    }
    /* Add css selector with #id to increase priority
      (otherwise ".gr-form-styles section" rule wins) */
    .hideItem,
    #itemAnnotationSection.hideItem {
      display: none;
    }
  </style>
  <div class="gr-form-styles">
    <div id="form">
      <section id="itemNameSection">
        <span class="title">[[detailType]] name</span>
        <iron-input bind-value="{{_itemName}}">
          <input placeholder="[[detailType]] Name" />
        </iron-input>
      </section>
      <section id="itemRevisionSection">
        <span class="title">Initial Revision</span>
        <iron-input bind-value="{{_itemRevision}}">
          <input placeholder="Revision (Branch or SHA-1)" />
        </iron-input>
      </section>
      <section
        id="itemAnnotationSection"
        class$="[[_computeHideItemClass(itemDetail)]]"
      >
        <span class="title">Annotation</span>
        <iron-input bind-value="{{_itemAnnotation}}">
          <input placeholder="Annotation (Optional)" />
        </iron-input>
      </section>
    </div>
  </div>
`;
