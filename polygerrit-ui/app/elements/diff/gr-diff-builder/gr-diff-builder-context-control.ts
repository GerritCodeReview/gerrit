import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-builder-context-control_html';
import {customElement, property, observe} from '@polymer/decorators';

@customElement('gr-diff-builder-context-control')
export class GrDiffBuilderContextControl extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }
}
declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-builder-context-control': GrDiffBuilderContextControl;
  }
}
