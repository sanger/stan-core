package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.Collection;

public interface RegisterValidation {
    Collection<String> validate();

    Donor getDonor(String name);

    Hmdmc getHmdmc(String hmdmc);

    SpatialLocation getSpatialLocation(String tissueTypeName, int code);

    LabwareType getLabwareType(String name);

    Medium getMedium(String name);

    Fixative getFixative(String name);

    Tissue getTissue(String externalName);
}
