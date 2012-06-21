package com.google.gerrit.client.patches;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwtorm.client.KeyUtil;

import java.util.LinkedList;
import java.util.List;

public class PatchSetSelectBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetSelectBox> {
  }

  private static Binder uiBinder = GWT.create(Binder.class);

  interface BoxStyle extends CssResource {
    String selected();
    String deselected();
  }

  PatchScript script;
  Patch.Key patchKey;
  PatchSet.Id idSideA;
  PatchSet.Id idSideB;
  PatchSet.Id idActive;
  Side side;
  PatchScreen.Type screenType;
  List<Anchor> links;

  @UiField FlowPanel linkPanel;
  @UiField SimplePanel downloadPanel;

  @UiField BoxStyle style;

  public enum Side{
    A, B
  }

  public PatchSetSelectBox(Side side) {
    this.side = side;

    initWidget(uiBinder.createAndBindUi(this));
  }

  public void display(final PatchScript script, Patch.Key key, PatchSet.Id idSideA, PatchSet.Id idSideB, final PatchScreen.Type type) {
    this.script = script;
    this.patchKey = key;
    this.idSideA = idSideA;
    this.idSideB = idSideB;
    this.idActive = (side == Side.A)?idSideA:idSideB;
    this.screenType = type;
    this.links = new LinkedList<Anchor>();

    linkPanel.add(new Label(PatchUtil.C.patchSet() + " "));

    if (side == Side.A) {
      addLink("Base", null);
    } else {
      links.add(null);
    }

    for(Patch patch : script.getHistory()) {
      PatchSet.Id psId = patch.getKey().getParentKey();
      addLink(Integer.toString(psId.get()), psId);
    }

    if (idActive == null && side == Side.A) {
      links.get(0).setStyleName(style.selected());
    } else {
      links.get(idActive.get()).setStyleName(style.selected());
    }

    downloadLink();
  }

  private void addLink(String label, final PatchSet.Id id) {
    final Anchor anchor = new Anchor(label);
    anchor.addClickHandler(new ClickHandler(){
      @Override
      public void onClick(ClickEvent event) {
        if (side == Side.A) {
          idSideA = id;
        } else {
          idSideB = id;
        }

        Patch.Key k = new Patch.Key(idSideB, patchKey.get());

        switch (screenType) {
          case SIDE_BY_SIDE:
            Gerrit.display(Dispatcher.toPatchSideBySide(idSideA, k));
            break;
          case UNIFIED:
            Gerrit.display(Dispatcher.toPatchUnified(idSideA, k));
            break;
        }
      }

    });

    links.add(anchor);
    linkPanel.add(anchor);
  }

  private void downloadLink() {
    downloadPanel.clear();

    boolean isCommitMessage = Patch.COMMIT_MSG.equals(script.getNewName());

    if (isCommitMessage || (side == Side.A && 0 < script.getA().size())
        || (side == Side.B && 0 < script.getB().size())) {
      return;
    }

    Patch.Key key = (idSideA == null)?patchKey:(new Patch.Key(idSideA, patchKey.get()));

    String sideURL = (side == Side.A)?"0":"1";
    final String base = GWT.getHostPageBaseURL() + "cat/";

    final Anchor anchor = new Anchor(PatchUtil.C.download());
    anchor.setHref(base + KeyUtil.encode(key.toString()) + "^" + sideURL);

    downloadPanel.add(anchor);
  }
}
