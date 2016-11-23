// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.KeyResources;

import java.util.Map;



public  class OperatorHelpPopup extends PopupPanel implements
    KeyPressHandler{
  private final FocusPanel focus;
  private int row = 0;
  private int column = 0;

  public OperatorHelpPopup() {
    super(true/* autohide */, true/* modal */);
    setStyleName(KeyResources.I.css().helpPopup());

    final Anchor closer = new Anchor(OperatorConstants.O.close());
    closer.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        hide();
      }
    });

    final Grid header = new Grid(1, 3);
    header.setStyleName(KeyResources.I.css().helpHeader());
    header.setText(0, 0, OperatorConstants.O.searchOperators());
    header.setWidget(0, 2, closer);

    final CellFormatter fmt = header.getCellFormatter();
    fmt.addStyleName(0, 1, KeyResources.I.css().helpHeaderGlue());
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    final Grid operatorList= new Grid(16, 5);
    operatorList.setStyleName(KeyResources.I.css().helpTable());
    loadList(operatorList);

    final FlowPanel body = new FlowPanel();
    body.add(header);
    body.getElement().appendChild(DOM.createElement("hr"));
    body.add(operatorList);
    focus = new FocusPanel(body);
    focus.addKeyPressHandler(this);
    focus.getElement().getStyle().setProperty("outline", "0px");
    focus.getElement().setAttribute("hideFocus", "true");
    add(focus);
  }

  @Override
  public void setVisible(final boolean show) {
    super.setVisible(show);
    if (show) {
      focus.setFocus(true);
    }
  }

  private void addOperator(final Map <String, String> desMap, final String keyName, Grid group){
    final Anchor operatorAnchor = new Anchor(desMap.get(keyName + "Key"));
    operatorAnchor.setStyleName(KeyResources.I.css().Operator());
    group.setWidget(row, column, operatorAnchor);
    listFormat();

    operatorAnchor.addClickHandler(new ClickHandler() {
    @Override
      public void onClick(final ClickEvent event) {
        final OperatorDesPopup DescriptionPopup = new OperatorDesPopup(desMap, keyName);

        DescriptionPopup.setPopupPositionAndShow(new PositionCallback() {
          @Override
          public void setPosition(final int pWidth, final int pHeight) {
            int x = (event.getClientX());
            int y = (event.getClientY());
            DescriptionPopup.setPopupPosition(x, y);
          }
        });
      }
    });
  }

  private void addTab(String tab, Grid group){
    final Label operatorTab = new Label(tab);
    operatorTab.setStyleName(KeyResources.I.css().OperatorTab());
    group.setWidget(row, column, operatorTab);
    listFormat();
  }

  private void listFormat(){
    if(row == 15){
      column += 1;
      row = 0;
    }
    else
      row += 1;
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    hide();
  }

  private void loadList(Grid list){
    addTab(OperatorConstants.O.ageTab(), list);
    addOperator(OperatorConstants.O.ages(), "age", list);
    addTab(OperatorConstants.O.timeTab(), list);
    addOperator(OperatorConstants.O.befores(), "before", list);
    addOperator(OperatorConstants.O.untils(), "until", list);
    addOperator(OperatorConstants.O.afters(), "after", list);
    addOperator(OperatorConstants.O.sinces(), "since", list);
    addTab(OperatorConstants.O.userTab(), list);
    addOperator(OperatorConstants.O.owners(), "owner", list);
    addOperator(OperatorConstants.O.ownerins(), "ownerin", list);
    addOperator(OperatorConstants.O.reviewers(), "reviewer", list);
    addOperator(OperatorConstants.O.reviewerins(), "reviewerin", list);
    addOperator(OperatorConstants.O.combys(), "comby", list);
    addOperator(OperatorConstants.O.froms(), "from", list);
    addOperator(OperatorConstants.O.reviewedbys(), "reviewedby", list);
    addOperator(OperatorConstants.O.authors(), "author", list);
    addOperator(OperatorConstants.O.committers(), "committer", list);
    addTab(OperatorConstants.O.idTab(), list);
    addOperator(OperatorConstants.O.changes(), "change", list);
    addOperator(OperatorConstants.O.conflictss(), "conflicts", list);
    addOperator(OperatorConstants.O.trs(), "tr", list);
    addOperator(OperatorConstants.O.bugs(), "bug", list);
    addTab(OperatorConstants.O.projectTab(), list);
    addOperator(OperatorConstants.O.projects(), "project", list);
    addOperator(OperatorConstants.O.projectss(), "projects", list);
    addOperator(OperatorConstants.O.parentprojects(), "parentproject", list);
    addTab(OperatorConstants.O.elementsTab(), list);
    addOperator(OperatorConstants.O.commits(), "commit", list);
    addOperator(OperatorConstants.O.branchs(), "branch", list);
    addOperator(OperatorConstants.O.topics(), "topic", list);
    addOperator(OperatorConstants.O.refs(), "ref", list);
    addOperator(OperatorConstants.O.labels(), "label", list);
    addOperator(OperatorConstants.O.messages(), "message", list);
    addOperator(OperatorConstants.O.comments(), "comment", list);
    addOperator(OperatorConstants.O.paths(), "path", list);
    addOperator(OperatorConstants.O.files(), "file", list);
    addTab(OperatorConstants.O.hasTab(), list);
    addOperator(OperatorConstants.O.drafts(), "draft", list);
    addOperator(OperatorConstants.O.stars(), "star", list);
    addTab(OperatorConstants.O.isTab(), list);
    addOperator(OperatorConstants.O.isstarreds(), "isstarred", list);
    addOperator(OperatorConstants.O.iswatcheds(), "iswatched", list);
    addOperator(OperatorConstants.O.isrevieweds(), "isreviewed", list);
    addOperator(OperatorConstants.O.isowners(), "isowner",list);
    addOperator(OperatorConstants.O.isreviewers(), "isreviewer", list);
    addOperator(OperatorConstants.O.isopens(), "isopen", list);
    addOperator(OperatorConstants.O.ispendings(), "ispending", list);
    addOperator(OperatorConstants.O.isdrafts(), "isdraft", list);
    addOperator(OperatorConstants.O.iscloseds(), "isclosed", list);
    addOperator(OperatorConstants.O.issubmitteds(), "issubmitted", list);
    addOperator(OperatorConstants.O.ismergeds(), "ismerged", list);
    addOperator(OperatorConstants.O.isabandoneds(), "isabandoned", list);
    addOperator(OperatorConstants.O.ismergeables(), "ismergeable", list);
    addTab(OperatorConstants.O.statusTab(), list);
    addOperator(OperatorConstants.O.statusopens(), "statusopen", list);
    addOperator(OperatorConstants.O.statuspendings(),"statuspending", list);
    addOperator(OperatorConstants.O.statusrevieweds(), "statusreviewed", list);
    addOperator(OperatorConstants.O.statussubmitteds(), "statussubmitted",list);
    addOperator(OperatorConstants.O.statuscloseds(), "statusclosed", list);
    addOperator(OperatorConstants.O.statusmergeds(), "statusmerged", list);
    addOperator(OperatorConstants.O.statusabandoneds(), "statusabandoned", list);
    addTab(OperatorConstants.O.relationTab(), list);
    addOperator(OperatorConstants.O.addeds(), "added", list);
    addOperator(OperatorConstants.O.deleteds(), "deleted", list);
    addOperator(OperatorConstants.O.deltas(), "delta", list);
    addTab(OperatorConstants.O.booleansTab(), list);
    addOperator(OperatorConstants.O.negations(), "negation", list);
    addOperator(OperatorConstants.O.ands(), "and", list);
    addOperator(OperatorConstants.O.ors(), "or", list);
    addTab(OperatorConstants.O.magicalTab(), list);
    addOperator(OperatorConstants.O.visibletos(), "visibleto", list);
    addOperator(OperatorConstants.O.isvisibles(), "isvisible", list);
    addOperator(OperatorConstants.O.starredbys(), "starredby", list);
    addOperator(OperatorConstants.O.watchedbys(),"watchedby", list);
    addOperator(OperatorConstants.O.draftbys(), "draftby", list);
    addOperator(OperatorConstants.O.limits(), "limits", list);
  }
}