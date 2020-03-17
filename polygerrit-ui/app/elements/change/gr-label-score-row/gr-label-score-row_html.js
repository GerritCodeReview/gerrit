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

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="/bower_components/iron-selector/iron-selector.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../../styles/gr-voting-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-label-score-row">
  <template>
    <style include="gr-voting-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
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
        --gr-button: {
          background-color: var(--button-background-color, var(--table-header-background-color));
          color: var(--primary-text-color);
          padding: 0 var(--spacing-m);
          @apply --vote-chip-styles;
        }
      }
      gr-button.iron-selected[vote="max"] {
        --button-background-color: var(--vote-color-approved);
      }
      gr-button.iron-selected[vote="positive"] {
        --button-background-color: var(--vote-color-recommended);
      }
      gr-button.iron-selected[vote="min"] {
        --button-background-color: var(--vote-color-rejected);
      }
      gr-button.iron-selected[vote="negative"] {
        --button-background-color: var(--vote-color-disliked);
      }
      gr-button.iron-selected[vote="neutral"] {
        --button-background-color: var(--vote-color-neutral);
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
    <span class="labelNameCell">[[label.name]]</span>
    <div class="buttonsCell">
      <template is="dom-repeat"
          items="[[_computeBlankItems(permittedLabels, label.name, 'start')]]"
          as="value">
        <span class="placeholder" data-label$="[[label.name]]"></span>
      </template>
      <iron-selector
          id="labelSelector"
          attr-for-selected="data-value"
          selected="[[_computeLabelValue(labels, permittedLabels, label)]]"
          hidden$="[[!_computeAnyPermittedLabelValues(permittedLabels, label.name)]]"
          on-selected-item-changed="_setSelectedValueText">
        <template is="dom-repeat"
            items="[[_items]]"
            as="value">
          <gr-button
              vote$="[[_computeVoteAttribute(value, index, _items.length)]]"
              has-tooltip
              data-name$="[[label.name]]"
              data-value$="[[value]]"
              title$="[[_computeLabelValueTitle(labels, label.name, value)]]">
            [[value]]</gr-button>
        </template>
      </iron-selector>
      <template is="dom-repeat"
          items="[[_computeBlankItems(permittedLabels, label.name, 'end')]]"
          as="value">
        <span class="placeholder" data-label$="[[label.name]]"></span>
      </template>
      <span class="labelMessage"
          hidden$="[[_computeAnyPermittedLabelValues(permittedLabels, label.name)]]">
        You don't have permission to edit this label.
      </span>
    </div>
    <div class$="selectedValueCell [[_computeHiddenClass(permittedLabels, label.name)]]">
      <span id="selectedValueLabel">[[_selectedValueText]]</span>
    </div>
  </template>
  <script src="gr-label-score-row.js"></script>
</dom-module>
