<!--
@license
Copyright (C) 2016 The Android Open Source Project

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
<link rel="import" href="/bower_components/paper-input/paper-input.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.html">
<link rel="import" href="../../shared/gr-autocomplete-dropdown/gr-autocomplete-dropdown.html">
<link rel="import" href="../../shared/gr-cursor-manager/gr-cursor-manager.html">
<link rel="import" href="../../shared/gr-icons/gr-icons.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-autocomplete">
  <template>
    <style include="shared-styles">
      .searchIcon {
        display: none;
      }
      .searchIcon.showSearchIcon {
        display: inline-block;
      }
      iron-icon {
        margin: 0 var(--spacing-xs);
        vertical-align: top;
      }
      paper-input.borderless {
        border: none;
        padding: 0;
      }
      paper-input {
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        padding: var(--spacing-s);
        --paper-input-container: {
          padding: 0;
        };
        --paper-input-container-input: {
          font-size: var(--font-size-normal);
          line-height: var(--line-height-normal);
        };
        /* This is a hack for not being able to set height:0 on the underline
           of a paper-input 2.2.3 element. All the underline fixes below only
           actually work in 3.x.x, so the height must be adjusted directly as
           a workaround until we are on Polymer 3. */
        height: var(--line-height-normal);
        --paper-input-container-underline-height: 0;
        --paper-input-container-underline-wrapper-height: 0;
        --paper-input-container-underline-focus-height: 0;
        --paper-input-container-underline-legacy-height: 0;
        --paper-input-container-underline: {
          height: 0;
          display: none;
        };
        --paper-input-container-underline-focus: {
          height: 0;
          display: none;
        };
        --paper-input-container-underline-disabled: {
          height: 0;
          display: none;
        };
      }
      paper-input.warnUncommitted {
        --paper-input-container-input: {
          color: var(--error-text-color);
          font-size: inherit;
        }
      }
    </style>
    <paper-input
        no-label-float
        id="input"
        class$="[[_computeClass(borderless)]]"
        disabled$="[[disabled]]"
        value="{{text}}"
        placeholder="[[placeholder]]"
        on-keydown="_handleKeydown"
        on-focus="_onInputFocus"
        on-blur="_onInputBlur"
        autocomplete="off">

      <!-- prefix as attribute is required to for polymer 1 -->
      <div slot="prefix" prefix>
        <iron-icon
          icon="gr-icons:search"
          class$="searchIcon [[_computeShowSearchIconClass(showSearchIcon)]]">
        </iron-icon>
      </div>
    </paper-input>
    <gr-autocomplete-dropdown
        vertical-align="top"
        vertical-offset="[[verticalOffset]]"
        horizontal-align="left"
        id="suggestions"
        on-item-selected="_handleItemSelect"
        on-keydown="_handleKeydown"
        suggestions="[[_suggestions]]"
        role="listbox"
        index="[[_index]]"
        position-target="[[_inputElement]]">
    </gr-autocomplete-dropdown>
  </template>
  <script src="gr-autocomplete.js"></script>
</dom-module>
