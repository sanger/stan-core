package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A record of an operation and the pass/fails that were recorded in it (along with comments)
 * @author dr6
 */
public class OpPassFail {
    /**
     * The pass/fail for this operation in a particular slot, indicated by an address
     */
    public static class SlotPassFail {
        private Address address;
        private PassFail result;
        private String comment;
        private List<Integer> sampleIds;

        public SlotPassFail() {}

        public SlotPassFail(Address address, PassFail result, String comment, List<Integer> sampleIds) {
            this.address = address;
            this.result = result;
            this.comment = comment;
            this.sampleIds = sampleIds;
        }

        /**
         * The address of the slot for this result
         * @return the address of a slot
         */
        public Address getAddress() {
            return this.address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        /**
         * The pass/fail result for this slot
         * @return a pass or fail
         */
        public PassFail getResult() {
            return this.result;
        }

        public void setResult(PassFail result) {
            this.result = result;
        }

        /**
         * The comment recorded for this slot in this operation. May be null.
         * (In the event that somehow multiple comments are associated with the same slot and samples,
         * this string may be multiple comments joined by new-lines.)
         * @return the comment (if any) for this slot
         */
        public String getComment() {
            return this.comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        /**
         * The sample ids associated with this result in this slot
         */
        public List<Integer> getSampleIds() {
            return this.sampleIds;
        }

        public void setSampleIds(List<Integer> sampleIds) {
            this.sampleIds = sampleIds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotPassFail that = (SlotPassFail) o;
            return (Objects.equals(this.address, that.address)
                    && this.result == that.result
                    && Objects.equals(this.comment, that.comment));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, result, comment);
        }

        @Override
        public String toString() {
            if (comment==null) {
                return String.format("(%s: %s)", address, result);
            }
            return String.format("(%s: %s, %s)", address, result, repr(comment));
        }
    }

    private Operation operation;
    private List<SlotPassFail> slotPassFails;

    public OpPassFail() {
        this(null, null);
    }

    public OpPassFail(Operation operation, List<SlotPassFail> slotPassFails) {
        this.operation = operation;
        setSlotPassFails(slotPassFails);
    }

    /**
     * The operation that recorded the included results
     */
    public Operation getOperation() {
        return this.operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    /**
     * The pass/fails and comments recorded in this operation on individual slots
     */
    public List<SlotPassFail> getSlotPassFails() {
        return this.slotPassFails;
    }

    public void setSlotPassFails(List<SlotPassFail> slotPassFails) {
        this.slotPassFails = (slotPassFails == null ? List.of() : slotPassFails);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpPassFail that = (OpPassFail) o;
        return (Objects.equals(this.operation, that.operation)
                && Objects.equals(this.slotPassFails, that.slotPassFails));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, slotPassFails);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("OpPassFail")
                .add("operation", operation == null ? null : operation.getId())
                .add("slotPassFails", slotPassFails)
                .toString();
    }
}
