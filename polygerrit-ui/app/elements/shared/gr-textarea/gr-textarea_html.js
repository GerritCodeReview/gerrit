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

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.html">
<link rel="import" href="../../shared/gr-autocomplete-dropdown/gr-autocomplete-dropdown.html">
<link rel="import" href="../../shared/gr-cursor-manager/gr-cursor-manager.html">
<link rel="import" href="../../shared/gr-overlay/gr-overlay.html">
<link rel="import" href="/bower_components/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html">
<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../core/gr-reporting/gr-reporting.html">

<dom-module id="gr-textarea">
  <template>
    <style include="shared-styles">
      :host {
        display: flex;
        position: relative;
      }
      :host(.monospace) {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        font-weight: var(--font-weight-normal);
      }
      :host(.code) {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-code);
        font-weight: var(--font-weight-normal);
      }
      #emojiSuggestions {
        font-family: var(--font-family);
      }
      gr-autocomplete {
        display: inline-block
      }
      #textarea {
        background-color: var(--view-background-color);
        width: 100%;
      }
      #hiddenText #emojiSuggestions {
        visibility: visible;
        white-space: normal;
      }
      iron-autogrow-textarea {
        position: relative;

        /** This is needed for firefox */
        --iron-autogrow-textarea_-_white-space: pre-wrap;
      }
      #textarea.noBorder {
        border: none;
      }
      #hiddenText {
        display: block;
        float: left;
        position: absolute;
        visibility: hidden;
        width: 100%;
        white-space: pre-wrap;
      }
    </style>
    <div id="hiddenText"></div>
    <!-- When the autocomplete is open, the span is moved at the end of
      hiddenText in order to correctly position the dropdown. After being moved,
      it is set as the positionTarget for the emojiSuggestions dropdown. -->
    <span id="caratSpan"></span>
    <gr-autocomplete-dropdown
        vertical-align="top"
        horizontal-align="left"
        dynamic-align
        id="emojiSuggestions"
        suggestions="[[_suggestions]]"
        index="[[_index]]"
        vertical-offset="[[_verticalOffset]]"
        on-dropdown-closed="_resetEmojiDropdown"
        on-item-selected="_handleEmojiSelect">
    </gr-autocomplete-dropdown>
    <iron-autogrow-textarea
        id="textarea"
        autocomplete="[[autocomplete]]"
        placeholder=[[placeholder]]
        disabled="[[disabled]]"
        rows="[[rows]]"
        max-rows="[[maxRows]]"
        value="{{text}}"
        on-bind-value-changed="_onValueChanged"></iron-autogrow-textarea>
    <gr-reporting id="reporting"></gr-reporting>
  </template>
  <script src="gr-textarea.js"></script>
</dom-module>
