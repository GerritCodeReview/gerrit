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

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.html">
<link rel="import" href="../../../behaviors/rest-client-behavior/rest-client-behavior.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-related-changes-list">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
      }
      h3 {
        margin: var(--spacing-m) 0 0;
      }
      section {
        margin-bottom: 1.4em; /* Same as line height for collapse purposes */
      }
      a {
        display: block;
      }
      .changeContainer,
      a {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .changeContainer {
        display: flex;
      }
      .changeContainer.thisChange:before {
        content: '➔';
        width: 1.2em;
      }
      h4,
      section div {
        display: flex;
      }
      h4:before,
      section div:before {
        content: ' ';
        flex-shrink: 0;
        width: 1.2em;
      }
      .note {
        color: var(--error-text-color);
      }
      .relatedChanges a {
        display: inline-block;
      }
      .strikethrough {
        color: var(--deemphasized-text-color);
        text-decoration: line-through;
      }
      .status {
        color: var(--deemphasized-text-color);
        font-weight: var(--font-weight-bold);
        margin-left: var(--spacing-xs);
      }
      .notCurrent {
        color: #e65100;
      }
      .indirectAncestor {
        color: #33691e;
      }
      .submittable {
        color: #1b5e20;
      }
      .submittableCheck {
        color: var(--vote-text-color-recommended);
        display: none;
      }
      .submittableCheck.submittable {
        display: inline;
      }
      .hidden,
      .mobile {
        display: none;
      }
       @media screen and (max-width: 60em) {
        .mobile {
          display: block;
        }
      }
    </style>
    <div>
      <section class="relatedChanges" hidden$="[[!_relatedResponse.changes.length]]" hidden>
        <h4>Relation chain</h4>
        <template
            is="dom-repeat"
            items="[[_relatedResponse.changes]]"
            as="related">
          <div class$="rightIndent [[_computeChangeContainerClass(change, related)]]">
            <a href$="[[_computeChangeURL(related._change_number, related.project, related._revision_number)]]"
                class$="[[_computeLinkClass(related)]]"
                title$="[[related.commit.subject]]">
              [[related.commit.subject]]
            </a>
            <span class$="[[_computeChangeStatusClass(related)]]">
              ([[_computeChangeStatus(related)]])
            </span>
          </div>
        </template>
      </section>
      <section
          id="submittedTogether"
          class$="[[_computeSubmittedTogetherClass(_submittedTogether)]]">
        <h4>Submitted together</h4>
        <template is="dom-repeat" items="[[_submittedTogether.changes]]" as="related">
          <div class$="[[_computeChangeContainerClass(change, related)]]">
            <a href$="[[_computeChangeURL(related._number, related.project)]]"
                class$="[[_computeLinkClass(related)]]"
                title$="[[related.project]]: [[related.branch]]: [[related.subject]]">
              [[related.project]]: [[related.branch]]: [[related.subject]]
            </a>
            <span
                tabindex="-1"
                title="Submittable"
                class$="submittableCheck [[_computeLinkClass(related)]]">✓</span>
          </div>
        </template>
        <template is="dom-if" if="[[_submittedTogether.non_visible_changes]]">
          <div class="note">
            [[_computeNonVisibleChangesNote(_submittedTogether.non_visible_changes)]]
          </div>
        </template>
      </section>
      <section hidden$="[[!_sameTopic.length]]" hidden>
        <h4>Same topic</h4>
        <template is="dom-repeat" items="[[_sameTopic]]" as="change">
          <div>
            <a href$="[[_computeChangeURL(change._number, change.project)]]"
                class$="[[_computeLinkClass(change)]]"
                title$="[[change.project]]: [[change.branch]]: [[change.subject]]">
              [[change.project]]: [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden$="[[!_conflicts.length]]" hidden>
        <h4>Merge conflicts</h4>
        <template is="dom-repeat" items="[[_conflicts]]" as="change">
          <div>
            <a href$="[[_computeChangeURL(change._number, change.project)]]"
                class$="[[_computeLinkClass(change)]]"
                title$="[[change.subject]]">
              [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden$="[[!_cherryPicks.length]]" hidden>
        <h4>Cherry picks</h4>
        <template is="dom-repeat" items="[[_cherryPicks]]" as="change">
          <div>
            <a href$="[[_computeChangeURL(change._number, change.project)]]"
                class$="[[_computeLinkClass(change)]]"
                title$="[[change.branch]]: [[change.subject]]">
              [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
    </div>
    <div hidden$="[[!loading]]">Loading...</div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-related-changes-list.js"></script>
</dom-module>
