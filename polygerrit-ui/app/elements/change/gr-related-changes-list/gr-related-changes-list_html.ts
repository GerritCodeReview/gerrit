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
    :host {
      display: block;
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
    .arrowToCurrentChange {
      position: absolute;
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
    .submittableCheck {
      padding-left: var(--spacing-s);
      color: var(--positive-green-text-color);
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
    <gr-endpoint-decorator name="related-changes-section">
      <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
      <gr-endpoint-slot name="top"></gr-endpoint-slot>
      <section
        class="relatedChanges"
        hidden$="[[!_relatedResponse.changes.length]]"
        hidden=""
      >
        <h4>Relation chain</h4>
        <template
          is="dom-repeat"
          items="[[_relatedResponse.changes]]"
          as="related"
        >
          <template is="dom-if" if="[[_changesEqual(related, change)]]">
            <span
              role="img"
              class="arrowToCurrentChange"
              aria-label="Arrow marking current change"
              >➔</span
            >
          </template>
          <div class="rightIndent changeContainer">
            <a
              href$="[[_computeChangeURL(related._change_number, related.project, related._revision_number)]]"
              class$="[[_computeLinkClass(related)]]"
              title$="[[related.commit.subject]]"
              on-click="_reportClick"
            >
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
        class$="[[_computeSubmittedTogetherClass(_submittedTogether)]]"
      >
        <h4>Submitted together</h4>
        <template
          is="dom-repeat"
          items="[[_submittedTogether.changes]]"
          as="related"
        >
          <template is="dom-if" if="[[_changesEqual(related, change)]]">
            <span
              role="img"
              class="arrowToCurrentChange"
              aria-label="Arrow marking current change"
              >➔</span
            >
          </template>
          <div class="changeContainer">
            <a
              href$="[[_computeChangeURL(related._number, related.project)]]"
              class$="[[_computeLinkClass(related)]]"
              title$="[[related.project]]: [[related.branch]]: [[related.subject]]"
              on-click="_reportClick"
            >
              [[related.project]]: [[related.branch]]: [[related.subject]]
            </a>
            <span
              tabindex="-1"
              title="Submittable"
              class$="submittableCheck [[_computeLinkClass(related)]]"
              role="img"
              aria-label="Submittable"
              >✓</span
            >
          </div>
        </template>
        <template is="dom-if" if="[[_submittedTogether.non_visible_changes]]">
          <div class="note">
            [[_computeNonVisibleChangesNote(_submittedTogether.non_visible_changes)]]
          </div>
        </template>
      </section>
      <section hidden$="[[!_sameTopic.length]]" hidden="">
        <h4>Same topic</h4>
        <template is="dom-repeat" items="[[_sameTopic]]" as="change">
          <div>
            <a
              href$="[[_computeChangeURL(change._number, change.project)]]"
              class$="[[_computeLinkClass(change)]]"
              title$="[[change.project]]: [[change.branch]]: [[change.subject]]"
              on-click="_reportClick"
            >
              [[change.project]]: [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden$="[[!_conflicts.length]]" hidden="">
        <h4>Merge conflicts</h4>
        <template is="dom-repeat" items="[[_conflicts]]" as="change">
          <div>
            <a
              href$="[[_computeChangeURL(change._number, change.project)]]"
              class$="[[_computeLinkClass(change)]]"
              title$="[[change.subject]]"
            >
              [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <section hidden$="[[!_cherryPicks.length]]" hidden="">
        <h4>Cherry picks</h4>
        <template is="dom-repeat" items="[[_cherryPicks]]" as="change">
          <div>
            <a
              href$="[[_computeChangeURL(change._number, change.project)]]"
              class$="[[_computeLinkClass(change)]]"
              title$="[[change.branch]]: [[change.subject]]"
              on-click="_reportClick"
            >
              [[change.branch]]: [[change.subject]]
            </a>
          </div>
        </template>
      </section>
      <gr-endpoint-slot name="bottom"></gr-endpoint-slot>
    </gr-endpoint-decorator>
  </div>
  <div hidden$="[[!loading]]">Loading...</div>
`;
