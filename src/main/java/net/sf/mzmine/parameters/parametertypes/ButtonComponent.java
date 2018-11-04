/*
 * Copyright 2006-2018 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package net.sf.mzmine.parameters.parametertypes;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;

public class ButtonComponent extends JPanel {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  private final JButton button;

  private Consumer<ActionEvent> consumer;


  public ButtonComponent(String label, Consumer<ActionEvent> consumer) {
    this.consumer = consumer;
    button = new JButton(label);
    button.addActionListener(e -> consumer.accept(e));
    add(button);
  }

  public void setConsumer(Consumer<ActionEvent> consumer) {
    this.consumer = consumer;
  }

  public void setText(String text) {
    button.setText(text);
  }

  public String getText() {
    return button.getText().trim();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    button.setEnabled(enabled);
  }

  @Override
  public void setToolTipText(String toolTip) {
    button.setToolTipText(toolTip);
  }

}