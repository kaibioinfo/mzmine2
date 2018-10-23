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

package net.sf.mzmine.modules.peaklistmethods.io.csvexport;

public enum ExportRowCommonElement {

  ROW_ID("Export row ID"), //
  ROW_MZ("Export row m/z"), //
  ROW_RT("Export row retention time"), //
  ROW_IDENTITY("Export row identity (main ID)"), //
  ROW_IDENTITY_ALL("Export row identity (all IDs)"), //
  ROW_IDENTITY_DETAILS("Export row identity (main ID + details)"), //
  ROW_COMMENT("Export row comment"), //
  ROW_PEAK_NUMBER("Export row number of detected peaks"), //
  ROW_CORR_GROUP_ID("Export correlation group ID"), //
  ROW_MOL_NETWORK_ID("Export annotation network number"), //
  ROW_BEST_ANNOTATION("Export best ion annotation"), //
  ROW_BEST_ANNOTATION_AND_SUPPORT("Export best ion annotation (+support)"), //
  ROW_NEUTRAL_MASS("Export neutral M mass");

  private final String name;

  ExportRowCommonElement(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return this.name;
  }
}
