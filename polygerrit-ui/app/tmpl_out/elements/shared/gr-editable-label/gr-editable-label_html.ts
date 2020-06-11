import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrEditableLabel} from '../../../../elements/shared/gr-editable-label/gr-editable-label';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrEditableLabelCheck extends GrEditableLabel
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (!this.showAsEditPencil)
    {
      {
        const el: HTMLElementTagNameMap['label'] = null!;
        useVars(el);
        el.setAttribute('class', `${this._computeLabelClass(this.readOnly, this.value, this.placeholder)}`);
        el.setAttribute('title', `${this._computeLabel(this.value, this.placeholder)}`);
        el.setAttribute('ariaLabel', `${this._computeLabel(this.value, this.placeholder)}`);
        el.addEventListener('click', this._showDropdown.bind(this));
      }
      setTextContent(`${this._computeLabel(this.value, this.placeholder)}`);

    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this.showAsEditPencil)
    {
      {
        const el: HTMLElementTagNameMap['gr-button'] = null!;
        useVars(el);
        el.link = true;
        el.setAttribute('class', `pencil ${this._computeLabelClass(this.readOnly, this.value, this.placeholder)}`);
        el.addEventListener('click', this._showDropdown.bind(this));
        el.title = this._computeLabel(this.value, this.placeholder);
      }
      {
        const el: HTMLElementTagNameMap['iron-icon'] = null!;
        useVars(el);
      }
    }
    {
      const el: HTMLElementTagNameMap['iron-dropdown'] = null!;
      useVars(el);
      el.setAttribute('id', `dropdown`);
      el.verticalOffset = this._verticalOffset;
      el.addEventListener('iron-overlay-canceled', this._cancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `dropdown-content`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `inputContainer`);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (!this.autocomplete)
    {
      {
        const el: HTMLElementTagNameMap['paper-input'] = null!;
        useVars(el);
        el.setAttribute('id', `input`);
        el.label = this.labelText;
        el.maxlength = this.maxLength;
        el.value = this._inputText;
        this._inputText = el.value;
      }
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this.autocomplete)
    {
      {
        const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
        useVars(el);
        el.label = this.labelText;
        el.setAttribute('id', `autocomplete`);
        el.text = this._inputText;
        this._inputText = el.text;
        el.query = this.query;
        el.addEventListener('commit', this._handleCommit.bind(this));
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `buttons`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('id', `cancelBtn`);
      el.addEventListener('click', this._cancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('id', `saveBtn`);
      el.addEventListener('click', this._save.bind(this));
    }
  }
}

