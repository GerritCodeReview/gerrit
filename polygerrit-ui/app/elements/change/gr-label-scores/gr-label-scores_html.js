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
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../gr-label-score-row/gr-label-score-row.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-label-scores">
  <template>
    <style include="shared-styles">
      .scoresTable {
        display: table;
        width: 100%;
      }
      .mergedMessage {
        font-style: italic;
        text-align: center;
        width: 100%;
      }
      gr-label-score-row:hover {
        background-color: var(--hover-background-color);
      }
      gr-label-score-row {
        display: table-row;
      }
      gr-label-score-row.no-access {
        display: var(--label-no-access-display, table-row);
      }
    </style>
    <div class="scoresTable">
      <template is="dom-repeat" items="[[_labels]]" as="label">
        <gr-label-score-row
            class$="[[_computeLabelAccessClass(label.name, permittedLabels)]]"
            label="[[label]]"
            name="[[label.name]]"
            labels="[[change.labels]]"
            permitted-labels="[[permittedLabels]]"
            label-values="[[_labelValues]]"></gr-label-score-row>
      </template>
    </div>
    <div class="mergedMessage"
        hidden$="[[!_changeIsMerged(change.status)]]">
      Because this change has been merged, votes may not be decreased.
    </div>
  </template>
  <script src="gr-label-scores.js"></script>
</dom-module>
