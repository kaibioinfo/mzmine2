package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import net.sf.mzmine.datamodel.PeakListRow;

/**
 * row to row correlation (2 rows) Intensity profile and peak shape correlation
 * 
 * @author Robin Schmid
 *
 */
public class R2RCorrelationData {

  public enum NegativeMarker {
    // intensity range is not shared between these two rows
    // at least in one raw data file: the features are out of RT range
    // the features do not overlap with X % of their intensity
    FeaturesDoNotOverlap, //
    MinFeaturesRequirementNotMet; //
  }

  // correlation of a to b
  private PeakListRow a, b;

  // ANTI CORRELATION MARKERS
  // to be used to exclude rows from beeing grouped
  private List<NegativeMarker> negativMarkers;

  public R2RCorrelationData(PeakListRow a, PeakListRow b) {
    this.a = a;
    this.b = b;
  }

  /**
   * 
   * @return List of negativ markers (non-null)
   */
  public @Nonnull List<NegativeMarker> getNegativMarkers() {
    return negativMarkers == null ? new ArrayList<>() : negativMarkers;
  }

  public int getNegativMarkerCount() {
    return negativMarkers == null ? 0 : negativMarkers.size();
  }

  /**
   * Negativ marker for this correlation (exclude from further grouping)
   * 
   * @param nm
   */
  public void addNegativMarker(NegativeMarker nm) {
    if (negativMarkers == null)
      negativMarkers = new ArrayList<>();
    negativMarkers.add(nm);
  }

  public PeakListRow getRowA() {
    return a;
  }

  public PeakListRow getRowB() {
    return b;
  }

  public boolean hasFeatureShapeCorrelation() {
    return false;
  }

  public double getAvgPeakShapeR() {
    return 0;
  }

}
