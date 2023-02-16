import {customElement, property, query} from 'lit/decorators.js';
import {css, html, LitElement, PropertyValueMap} from 'lit';
import {DiffInfo} from '../../../types/diff';

const monacoWorkerUrl = `${
  window.STATIC_RESOURCE_PATH ?? ''
}/workers/monaco-worker.js`;

const eventTypes = {
  ready: 'ready',
  diffChanged: 'diffChanged',
  pathChanged: 'pathChanged',
};

@customElement('gr-monaco-diff')
export class GrMonacoDiff extends LitElement {
  @query('iframe')
  private iframe?: HTMLIFrameElement;

  @property({type: Boolean, reflect: true})
  override hidden = false;

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: String})
  path?: string;

  get document() {
    return this.iframe?.contentWindow?.document;
  }

  //   return html` <gr-diff
  //   id="diff"
  //   .noAutoRender=${this.noAutoRender}
  //   .prefs=${this.prefs}
  //   .displayLine=${this.displayLine}
  //   .isImageDiff=${this.isImageDiff}
  //   .noRenderOnPrefsChange=${this.noRenderOnPrefsChange}
  //   .renderPrefs=${this.renderPrefs}
  //   .lineWrapping=${this.lineWrapping}
  //   .viewMode=${this.viewMode}
  //   .lineOfInterest=${this.lineOfInterest}
  //   .loggedIn=${this.loggedIn}
  //   .errorMessage=${this.errorMessage}
  //   .baseImage=${this.baseImage}
  //   .revisionImage=${this.revisionImage}
  //   .coverageRanges=${this.coverageRanges}
  //   .blame=${this.blame}
  //   .layers=${this.layers}
  //   .showNewlineWarningLeft=${showNewlineWarningLeft}
  //   .showNewlineWarningRight=${showNewlineWarningRight}
  //   .useNewImageDiffUi=${useNewImageDiffUi}
  // ></gr-diff>`;

  constructor() {
    super();
  }

  static override get styles() {
    return css`
      :host {
        display: block;
      }
      iframe {
        border: none;
        width: 100%;
        height: 100%;
        padding: 0;
      }
    `;
  }

  // gr-diff boilerplate
  cancel() {}
  getCursorStops() {
    return [];
  }
  isRangeSelected() {
    return false;
  }
  createRangeComment() {}
  toggleLeftDiff() {}
  clearDiffContent() {}

  override render() {
    return html` <iframe id="vscode"></iframe> `;
  }

  override firstUpdated(changedProperties: PropertyValueMap<this>) {
    super.firstUpdated(changedProperties);
    this.initMonaco();
  }

  private initMonaco() {
    const div = this.document!.createElement('div');
    div.id = 'monaco-container';
    this.document?.body.appendChild(div);
    const script = this.document!.createElement('script');
    script.src = window.location.href + '/' + monacoWorkerUrl;

    const head = this.document!.head;
    const style = this.document!.createElement('style');
    style.appendChild(
      this.document!.createTextNode(
        css`
          body {
            height: 100vh;
            overflow: hidden;
            margin: 0;
          }
          #monaco-container {
            width: 100%;
            height: 100%;
          }
          .debug-red {
            background: red;
          }
          .debug-green {
            background: green;
          }
          html,
          body {
            margin: 0px;
          }
        `.cssText
      )
    );
    head.appendChild(style);
    head.appendChild(script);
  }

  override updated(changedProperties: PropertyValueMap<this>) {
    super.updated(changedProperties);
    if (changedProperties.has('diff') && this.diff) {
      this.postMessage(eventTypes.diffChanged, this.diff);
    }
    if (changedProperties.has('path') && this.path) {
      this.postMessage(eventTypes.pathChanged, this.path);
    }
  }

  postMessage(event: string, payload: unknown) {
    if (!this.iframe) {
      return;
    }
    this.iframe.contentWindow!.postMessage(
      JSON.stringify({event, payload}),
      window.location.href
    );
  }
}
