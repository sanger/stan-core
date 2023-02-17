package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public class ReleaseRequest {

    /**
     * Details of a particular labware being released.
     * @author dr6
     */
    public static class ReleaseLabware {
        private String barcode;
        private String workNumber;

        public ReleaseLabware(String barcode, String workNumber) {
            this.barcode = barcode;
            this.workNumber = workNumber;
        }

        public ReleaseLabware(String barcode) {
            this(barcode, null);
        }

        public ReleaseLabware() {}

        /**
         * The barcode of the labware to release.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        /**
         * The work number (optional) to associate with the release.
         */
        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReleaseLabware that = (ReleaseLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, workNumber);
        }

        @Override
        public String toString() {
            if (workNumber==null) {
                return "("+repr(barcode)+")";
            }
            return String.format("(%s for %s)", repr(barcode), repr(workNumber));
        }
    }

    private List<ReleaseLabware> releaseLabware;
    private String destination;
    private String recipient;

    public ReleaseRequest() {
        setReleaseLabware(null);
    }

    public ReleaseRequest(List<ReleaseLabware> releaseLabware, String destination, String recipient) {
        setReleaseLabware(releaseLabware);
        setDestination(destination);
        setRecipient(recipient);
    }

    public List<ReleaseLabware> getReleaseLabware() {
        return this.releaseLabware;
    }

    public void setReleaseLabware(List<ReleaseLabware> releaseLabware) {
        this.releaseLabware = coalesce(releaseLabware, List.of());
    }

    public String getDestination() {
        return this.destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getRecipient() {
        return this.recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseRequest that = (ReleaseRequest) o;
        return (Objects.equals(this.releaseLabware, that.releaseLabware)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.recipient, that.recipient));
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseLabware, destination, recipient);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("releaseLabware", releaseLabware)
                .add("destination", repr(destination))
                .add("recipient", repr(recipient))
                .toString();
    }
}
