import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {unsafeHTML} from 'lit/directives/unsafe-html.js';

@customElement('gr-html-renderer')
export class GrHtmlRenderer extends LitElement {
  @property({type: String})
  htmlContent?: string;

  override render() {
    return html`${unsafeHTML(this.htmlContent ?? '')}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-html-renderer': GrHtmlRenderer;
  }
}
