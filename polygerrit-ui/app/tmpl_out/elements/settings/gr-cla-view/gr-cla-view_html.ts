import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrClaView} from '../../../../elements/settings/gr-cla-view/gr-cla-view';

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

export class GrClaViewCheck extends GrClaView
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['main'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['h1'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-1`);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of __f(__f(this._serverConfig)!.auth)!.contributor_agreements!)
      {
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
          el.setAttribute('class', `contributorAgreementButton`);
        }
        {
          const el: HTMLElementTagNameMap['input'] = null!;
          useVars(el);
          el.setAttribute('id', `claNewAgreementsInput${__f(item)!.name}`);
          el.setAttribute('dataName', `${__f(item)!.name}`);
          el.setAttribute('dataUrl', `${__f(item)!.url}`);
          el.addEventListener('click', e => this._handleShowAgreement.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('disabled', `${this._disableAgreements(item, this._groups, this._signedAgreements)}`);
        }
        {
          const el: HTMLElementTagNameMap['label'] = null!;
          useVars(el);
          el.setAttribute('id', `claNewAgreementsLabel`);
        }
        setTextContent(`${__f(item)!.name}`);

        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `alreadySubmittedText ${this._hideAgreements(item, this._groups, this._signedAgreements)}`);
        }
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `agreementsUrl`);
        }
        setTextContent(`${__f(item)!.description}`);

      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `claNewAgreement`);
      el.setAttribute('class', `${this._computeShowAgreementsClass(this._showAgreements)}`);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `agreementsUrl`);
      el.setAttribute('class', `agreementsUrl`);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._agreementsUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `agreementsTextBox ${this._computeHideAgreementClass(this._agreementName, __f(__f(this._serverConfig)!.auth)!.contributor_agreements)}`);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._agreementsText;
      this._agreementsText = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `input-agreements`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this._handleSaveAgreements.bind(this));
      el.disabled = this._disableAgreementsText(this._agreementsText);
    }
  }
}

