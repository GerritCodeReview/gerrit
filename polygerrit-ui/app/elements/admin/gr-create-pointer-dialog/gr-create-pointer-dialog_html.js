import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
      /* Add css selector with #id to increase priority
      (otherwise ".gr-form-styles section" rule wins) */
      .hideItem,
      #itemAnnotationSection.hideItem {
        display: none;
      }
    </style>
    <div class="gr-form-styles">
      <div id="form">
        <section id="itemNameSection">
          <span class="title">[[detailType]] name</span>
          <iron-input placeholder="[[detailType]] Name" bind-value="{{_itemName}}">
            <input is="iron-input" placeholder="[[detailType]] Name" bind-value="{{_itemName}}">
          </iron-input>
        </section>
        <section id="itemRevisionSection">
          <span class="title">Initial Revision</span>
          <iron-input placeholder="Revision (Branch or SHA-1)" bind-value="{{_itemRevision}}">
            <input is="iron-input" placeholder="Revision (Branch or SHA-1)" bind-value="{{_itemRevision}}">
          </iron-input>
        </section>
        <section id="itemAnnotationSection" class\$="[[_computeHideItemClass(itemDetail)]]">
          <span class="title">Annotation</span>
          <iron-input placeholder="Annotation (Optional)" bind-value="{{_itemAnnotation}}">
            <input is="iron-input" placeholder="Annotation (Optional)" bind-value="{{_itemAnnotation}}">
          </iron-input>
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
