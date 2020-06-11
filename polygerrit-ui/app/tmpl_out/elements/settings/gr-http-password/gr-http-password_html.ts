import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrHttpPassword} from '../../../../elements/settings/gr-http-password/gr-http-password';

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

export class GrHttpPasswordCheck extends GrHttpPassword
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${this._passwordUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${this._username}`);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `generateButton`);
      el.addEventListener('click', this._handleGenerateTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this._passwordUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._passwordUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `generatedPasswordOverlay`);
      el.addEventListener('iron-overlay-closed', this._generatedPasswordOverlayClosed.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('id', `generatedPasswordDisplay`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${this._generatedPassword}`);

    {
      const el: HTMLElementTagNameMap['gr-copy-clipboard'] = null!;
      useVars(el);
      el.text = this._generatedPassword;
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('id', `passwordWarning`);
    }
    {
      const el: HTMLElementTagNameMap['br'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('class', `closeButton`);
      el.addEventListener('click', this._closeOverlay.bind(this));
    }
  }
}

