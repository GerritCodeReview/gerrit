/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';

import {EditorView, ViewUpdate, ViewPlugin, keymap, highlightSpecialChars, drawSelection, highlightActiveLine} from "@codemirror/view"
import {EditorState, Annotation} from "@codemirror/state"
import {history, historyKeymap} from "@codemirror/history"
import {foldGutter, foldKeymap} from "@codemirror/fold"
import {indentOnInput} from "@codemirror/language"
import {lineNumbers, highlightActiveLineGutter} from "@codemirror/gutter"
import {defaultKeymap} from "@codemirror/commands"
import {bracketMatching} from "@codemirror/matchbrackets"
import {closeBrackets, closeBracketsKeymap} from "@codemirror/closebrackets"
import {searchKeymap, highlightSelectionMatches} from "@codemirror/search"
import {autocompletion, completionKeymap} from "@codemirror/autocomplete"
import {commentKeymap} from "@codemirror/comment"
import {defaultHighlightStyle} from "@codemirror/highlight"
import {lintKeymap} from "@codemirror/lint"

import {markdown} from "@codemirror/lang-markdown"

const SetTextCalled = Annotation.define();


declare global {
  interface HTMLElementTagNameMap {
    'gr-default-editor': GrDefaultEditor;
  }
}

@customElement('gr-default-editor')
export class GrDefaultEditor extends LitElement {
  /**
   * Fired when the content of the editor changes.
   *
   * @event content-change
   */

  @property({type: String})
  set fileContent(t: string) {
    if (this.editor) {
      this.setText(t);
    } else {
      this._fileContent = t;
    }
  }

  @property()
  additionalCursors: Array<{ name: string; position: number; color: string }> =
    [];

  _fileContent: string | undefined;

  editor!: EditorView;

  @query('#editor')
  _editorEl!: HTMLElement;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: flex;
        }
        .cursor {
          position: fixed;
        }
      `
    ];
  }

  override render() {
    return html`<div style="flex: 1;" id="editor"></div>`;
  }

  override firstUpdated() {
    this.editor = new EditorView({
        state: EditorState.create({
		doc: this.fileContent,
		extensions: [
		  lineNumbers(),
		  highlightActiveLineGutter(),
		  highlightSpecialChars(),
		  history(),
		  foldGutter(),
		  drawSelection(),
		  EditorState.allowMultipleSelections.of(true),
		  indentOnInput(),
		  defaultHighlightStyle.fallback,
		  bracketMatching(),
		  closeBrackets(),
		  autocompletion(),
		  highlightActiveLine(),
		  highlightSelectionMatches(),
		  keymap.of([
		    ...closeBracketsKeymap,
		    ...defaultKeymap,
		    ...searchKeymap,
		    ...historyKeymap,
		    ...foldKeymap,
		    ...commentKeymap,
		    ...completionKeymap,
		    ...lintKeymap
		  ]),
                  markdown(),
		  this.eventPlugin()
		]
	}),
	parent: this._editorEl as Element,
      });
    if (this._fileContent) {
      this.setText(this._fileContent);
    }
    this.requestUpdate();

  }

  setText(t: string) {
    const { selection } = this.editor.state;

    const textLength = t.length;
    const anchor =
      selection.main.anchor > textLength ? textLength : selection.main.anchor;

    this.editor.dispatch({
      annotations: [SetTextCalled.of([])],
      changes: [
        {
          from: 0,
          to: this.editor.state.doc.length,
          insert: t,
        },
      ],
      selection: {
        anchor,
      },
    });

    this.editor.scrollPosIntoView(anchor);
  }

  eventPlugin() {
    const thisEl = this;
    return ViewPlugin.fromClass(
      class {
        update(update: ViewUpdate) {
          if (
            update.transactions.length > 0 &&
            update.transactions[0].annotation(SetTextCalled)
          ) {
            return;
          }

          if (!update.docChanged) return;

          var text = "";
	  for (let subtext of update.state.doc.iter()) {
		  text += subtext;
	  }
	  thisEl.dispatchEvent(
	      new CustomEvent('content-change', {
		      detail: {value: text},
		      bubbles: true,
		      composed: true,
	      })
          );
        }
      });
  }

}
