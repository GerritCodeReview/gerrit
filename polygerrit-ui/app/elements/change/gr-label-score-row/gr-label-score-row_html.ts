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
    .labelNameCell,
    .buttonsCell,
    .selectedValueCell {
      padding: var(--spacing-s) var(--spacing-m);
      display: table-cell;
    }
    /* We want the :hover highlight to extend to the border of the dialog. */
    .labelNameCell {
      padding-left: var(--spacing-xl);
    }
    .selectedValueCell {
      padding-right: var(--spacing-xl);
    }
    /* This is a trick to let the selectedValueCell take the remaining width. */
    .labelNameCell,
    .buttonsCell {
      white-space: nowrap;
    }
    .selectedValueCell {
      width: 75%;
    }
    .labelMessage {
      color: var(--deemphasized-text-color);
    }
    gr-button {
      min-width: 42px;
      box-sizing: border-box;
    }
    gr-button::part(paper-button) {
      background-color: var(
        --button-background-color,
        var(--table-header-background-color)
      );
      padding: 0 var(--spacing-m);
    }
    gr-button[vote='max'].iron-selected {
      --button-background-color: var(--vote-color-approved);
    }
    gr-button[vote='positive'].iron-selected {
      --button-background-color: var(--vote-color-recommended);
    }
    gr-button[vote='min'].iron-selected {
      --button-background-color: var(--vote-color-rejected);
    }
    gr-button[vote='negative'].iron-selected {
      --button-background-color: var(--vote-color-disliked);
    }
    gr-button[vote='neutral'].iron-selected {
      --button-background-color: var(--vote-color-neutral);
    }
    gr-button[vote='positive'].iron-selected::part(paper-button) {
      border-color: var(--vote-outline-recommended);
    }
    gr-button[vote='negative'].iron-selected::part(paper-button) {
      border-color: var(--vote-outline-disliked);
    }
    gr-button > gr-tooltip-content {
      margin: 0px -10px;
      padding: 0px 10px;
    }
    .placeholder {
      display: inline-block;
      width: 42px;
      height: 1px;
    }
    .placeholder::before {
      content: ' ';
    }
    .selectedValueCell {
      color: var(--deemphasized-text-color);
      font-style: italic;
    }
    .selectedValueCell.hidden {
      display: none;
    }
    @media only screen and (max-width: 50em) {
      .selectedValueCell {
        display: none;
      }
    }
  </style>
  <span class="labelNameCell" id="labelName" aria-hidden="true"
    >[[label.name]]</span
  >
  <div class="buttonsCell">
    <template
      is="dom-repeat"
      items="[[_computeBlankItems(permittedLabels, label.name, 'start')]]"
      as="value"
    >
      <span class="placeholder" data-label$="[[label.name]]"></span>
    </template>
    <iron-selector
      id="labelSelector"
      attr-for-selected="data-value"
      selected="[[_computeLabelValue(labels, permittedLabels, label)]]"
      hidden$="[[!_computeAnyPermittedLabelValues(permittedLabels, label.name)]]"
      on-selected-item-changed="_setSelectedValueText"
      role="radiogroup"
      aria-labelledby="labelName"
    >
      <template is="dom-repeat" items="[[_items]]" as="value">
        <gr-button
          role="radio"
          vote$="[[_computeVoteAttribute(value, index, _items.length)]]"
          title$="[[_computeLabelValueTitle(labels, label.name, value)]]"
          data-name$="[[label.name]]"
          data-value$="[[value]]"
          aria-label$="[[value]]"
          voteChip
        >
          <gr-tooltip-content
            has-tooltip
            title$="[[_computeLabelValueTitle(labels, label.name, value)]]"
          >
            [[value]]
          </gr-tooltip-content>
        </gr-button>
      </template>
    </iron-selector>
    <template
      is="dom-repeat"
      items="[[_computeBlankItems(permittedLabels, label.name, 'end')]]"
      as="value"
    >
      <span class="placeholder" data-label$="[[label.name]]"></span>
    </template>
    <span
      class="labelMessage"
      hidden$="[[_computeAnyPermittedLabelValues(permittedLabels, label.name)]]"
    >
      You don't have permission to edit this label.
    </span>
  </div>
  <div
    class$="selectedValueCell [[_computeHiddenClass(permittedLabels, label.name)]]"
  >
    <span id="selectedValueLabel">[[_selectedValueText]]</span>
  </div>
`;
