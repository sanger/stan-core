package uk.ac.sanger.sccp.stan.request.plan;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * @author dr6
 */
public class PlanGroup {
    private List<Address> addresses;

    public PlanGroup() {
        this(null);
    }

    public PlanGroup(List<Address> addresses) {
        setAddresses(addresses);
    }

    public List<Address> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = nullToEmpty(addresses);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlanGroup that = (PlanGroup) o;
        return Objects.equals(this.addresses, that.addresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses);
    }

    @Override
    public String toString() {
        return this.addresses.toString();
    }
}
