import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      #threads {
        display: block;
        padding: var(--spacing-l);
      }
      gr-comment-thread {
        display: block;
        margin-bottom: var(--spacing-m);
        max-width: 80ch;
      }
      .header {
        align-items: center;
        background-color: var(--table-header-background-color);
        border-bottom: 1px solid var(--border-color);
        border-top: 1px solid var(--border-color);
        display: flex;
        justify-content: left;
        min-height: 3.2em;
        padding: var(--spacing-m) var(--spacing-l);
      }
      .toggleItem.draftToggle {
        display: none;
      }
      .toggleItem.draftToggle.show {
        display: flex;
      }
      .toggleItem {
        align-items: center;
        display: flex;
        margin-right: var(--spacing-l);
      }
      .draftsOnly:not(.unresolvedOnly) gr-comment-thread[has-draft],
      .unresolvedOnly:not(.draftsOnly) gr-comment-thread[unresolved],
      .draftsOnly.unresolvedOnly gr-comment-thread[has-draft][unresolved] {
        display: block
      }
    </style>
    <template is="dom-if" if="[[!hideToggleButtons]]">
      <div class="header">
        <div class="toggleItem">
          <paper-toggle-button id="unresolvedToggle" checked="{{_unresolvedOnly}}"></paper-toggle-button>
            Only unresolved threads</div>
        <div class\$="toggleItem draftToggle [[_computeShowDraftToggle(loggedIn)]]">
          <paper-toggle-button id="draftToggle" checked="{{_draftsOnly}}"></paper-toggle-button>
            Only threads with drafts</div>
      </div>
    </template>
    <div id="threads">
      <template is="dom-if" if="[[!threads.length]]">
        [[_computeNoThreadsMessage(tab)]]
      </template>
      <template is="dom-repeat" items="[[_filteredThreads]]" as="thread" initial-count="5" target-framerate="60">
        <gr-comment-thread show-file-path="" change-num="[[changeNum]]" comments="[[thread.comments]]" comment-side="[[thread.commentSide]]" project-name="[[change.project]]" is-on-parent="[[_isOnParent(thread.commentSide)]]" line-num="[[thread.line]]" patch-num="[[thread.patchNum]]" path="[[thread.path]]" root-id="{{thread.rootId}}" on-thread-changed="_handleCommentsChanged" on-thread-discard="_handleThreadDiscard"></gr-comment-thread>
      </template>
    </div>
`;
