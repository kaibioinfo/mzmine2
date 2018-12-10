package net.sf.mzmine.datamodel.identities.iontype.networks;

import java.util.List;
import net.sf.mzmine.datamodel.identities.iontype.CombinedIonModification;
import net.sf.mzmine.datamodel.identities.iontype.IonModification;
import net.sf.mzmine.datamodel.identities.iontype.IonNetwork;

public class IonNetworkRelation extends IonNetworkRelationInterf {

  // the linked network
  private IonNetwork a;
  private IonNetwork b;
  // marker if one network is condensed of the other
  private boolean isCondensed;
  private boolean isModified;
  private IonModification modA;
  private IonModification modB;

  public IonNetworkRelation(IonNetwork a, IonNetwork link, boolean isCondensed, boolean isModified,
      List<IonModification> mods) {
    this(a, link, isCondensed, isModified,
        new CombinedIonModification(mods.toArray(new IonModification[0])));
  }

  public IonNetworkRelation(IonNetwork a, IonNetwork link, boolean isCondensed, boolean isModified,
      IonModification[] mods) {
    this(a, link, isCondensed, isModified, new CombinedIonModification(mods));
  }

  public IonNetworkRelation(IonNetwork a, IonNetwork link, boolean isCondensed, boolean isModified,
      IonModification mod) {
    this.a = a;
    this.b = link;
    // a is smaller neutral mass
    if (a.getNeutralMass() > b.getNeutralMass()) {
      IonNetwork tmp = a;
      a = b;
      b = tmp;
    }

    this.isCondensed = isCondensed;
    this.isModified = isModified;

    this.modA = mod;
    this.modB = mod.createOpposite();

    if (isCondensed) {
      // a is M(netID)
      modA = null;
      // b is 2Mcondensed(netID)
      modB = mod;
    } else {
      if (modA.getMass() > 0) {
        IonModification tmp = modA;
        modA = modB;
        modB = tmp;
      }
    }
  }

  public IonModification getMods() {
    return modA;
  }

  public boolean isCondensed() {
    return isCondensed;
  }

  public boolean isModified() {
    return isModified;
  }

  public IonNetwork getLink() {
    return b;
  }

  @Override
  public String getName(IonNetwork ionNetwork) {
    if (ionNetwork.getID() == a.getID()) {
      return parseNameA();
    } else if (ionNetwork.getID() == b.getID()) {
      return parseNameB();
    }
    return "";
  }

  private String parseNameA() {
    String name = "";
    if (isCondensed) {
      name += "M(" + b.getID() + "_condensed)";
    } else {
      name += "M(" + b.getID() + ")";
    }
    if (modA != null)
      name += modA.parseName();
    return name;
  }

  private String parseNameB() {
    String name = "";
    if (isCondensed) {
      name += "2Mcondensed(" + a.getID() + ")";
    } else {
      name += "M(" + a.getID() + ")";
    }
    if (modB != null)
      name += modB.parseName();
    return name;
  }

  @Override
  public String getDescription() {
    String desc = "";
    if (isCondensed)
      desc = "condensation (2X-->XX+H2O) ";
    desc += modB.parseName();
    return desc;
  }

  @Override
  public IonNetwork[] getAllNetworks() {
    return new IonNetwork[] {a, b};
  }
}
