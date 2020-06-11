import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDialog} from '../../../../elements/shared/gr-dialog/gr-dialog';

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

export class GrDialogCheck extends GrDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `container`);
      el.addEventListener('keydown', this._handleKeydown.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['header'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['main'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `overflow-container`);
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['footer'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['slot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `cancel`);
      el.setAttribute('class', `${this._computeCancelClass(this.cancelLabel)}`);
      el.link = true;
      el.addEventListener('click', this._handleCancelTap.bind(this));
    }
    setTextContent(`
        ${this.cancelLabel}
      `);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `confirm`);
      el.link = true;
      el.addEventListener('click', this._handleConfirm.bind(this));
      el.disabled = this.disabled;
      el.setAttribute('title', `${this.confirmTooltip}`);
    }
    setTextContent(`
        ${this.confirmLabel}
      `);

  }
}

