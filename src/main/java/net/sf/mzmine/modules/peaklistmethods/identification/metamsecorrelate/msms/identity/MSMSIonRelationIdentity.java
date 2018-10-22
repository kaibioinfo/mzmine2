package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity;

import net.sf.mzmine.datamodel.DataPoint;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.ESIAdductType;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;

/**
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class MSMSIonRelationIdentity extends MSMSIonIdentity {

  private DataPoint parentDP;
  protected double parentMZ;
  protected Relation relation = Relation.NEUTRAL_LOSS;

  public enum Relation {
    NEUTRAL_LOSS;
  }


  public MSMSIonRelationIdentity(MZTolerance mzTolerance, DataPoint dp, ESIAdductType type,
      double parent) {
    super(mzTolerance, dp, type);
    this.parentMZ = parent;
  }

  public MSMSIonRelationIdentity(MZTolerance mzTolerance, DataPoint dp, ESIAdductType type,
      DataPoint parent) {
    super(mzTolerance, dp, type);
    this.parentDP = parent;
  }

  @Override
  public String getName() {
    switch (relation) {
      case NEUTRAL_LOSS:
        return type.getName();
    }
    return super.getName();
  }

  public Relation getRelation() {
    return relation;
  }

  /**
   * MZ difference
   * 
   * @return
   */
  public double getMZDiff() {
    return getParentMZ() - this.getMZ();
  }

  public double getParentMZ() {
    return parentDP == null ? parentMZ : parentDP.getMZ();
  }
}
