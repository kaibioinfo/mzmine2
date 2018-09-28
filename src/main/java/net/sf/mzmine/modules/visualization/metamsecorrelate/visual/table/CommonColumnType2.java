/*
 * Copyright 2006-2015 The MZmine 2 Development Team
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

package net.sf.mzmine.modules.visualization.metamsecorrelate.visual.table;

import java.awt.Paint;
import net.sf.mzmine.datamodel.PeakIdentity;

// no peak shape column
public enum CommonColumnType2 {

  ROWID("ID", Integer.class), //
  COLOR("", Paint.class), GROUPID("#", Integer.class), //
  AVERAGEMZ("m/z", Double.class), //
  AVERAGERT("RT", Double.class), //
  IDENTITY("Identity", PeakIdentity.class), //
  COMMENT("Comment", String.class);

  private final String columnName;
  private final Class<?> columnClass;

  CommonColumnType2(String columnName, Class<?> columnClass) {
    this.columnName = columnName;
    this.columnClass = columnClass;
  }

  public String getColumnName() {
    return columnName;
  }

  public Class<?> getColumnClass() {
    return columnClass;
  }

  @Override
  public String toString() {
    return columnName;
  }

}
