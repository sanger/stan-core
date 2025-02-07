package uk.ac.sanger.sccp.stan.request.history;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * @author dr6
 */
public class HistoryGraph {

    public HistoryGraph(List<Node> nodes, List<Link> links) {
        this.nodes = nodes;
        this.links = links;
    }

    List<Node> nodes;
    List<Link> links;

    public static final class Node {
        private final int id;
        private final LocalDateTime time;
        private final String heading;
        private final String destBarcode;
        private final String user;
        private final String externalName;
        private final String bioStateDesc;
        private int x, y;

        public Node(int id, LocalDateTime time, String heading, String destBarcode, String user,
                    String externalName, String bioStateDesc) {
            this.id = id;
            this.time = time;
            this.heading = heading;
            this.destBarcode = destBarcode;
            this.user = user;
            this.externalName = externalName;
            this.bioStateDesc = bioStateDesc;
        }

        public int id() {
            return id;
        }

        public LocalDateTime time() {
            return time;
        }

        public String heading() {
            return heading;
        }

        public String destBarcode() {
            return destBarcode;
        }

        public String user() {
            return user;
        }

        public String externalName() {
            return externalName;
        }

        public String bioStateDesc() {
            return bioStateDesc;
        }

        public int getX() {
            return this.x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return this.y;
        }

        public void setY(int y) {
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Node) obj;
            return (this.id == that.id &&
                    Objects.equals(this.time, that.time) &&
                    Objects.equals(this.heading, that.heading) &&
                    Objects.equals(this.destBarcode, that.destBarcode) &&
                    Objects.equals(this.user, that.user) &&
                    Objects.equals(this.externalName, that.externalName) &&
                    Objects.equals(this.bioStateDesc, that.bioStateDesc));
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, time, heading, destBarcode, user, externalName, bioStateDesc);
        }

        @Override
        public String toString() {
            return describe(this)
                    .add("id", id)
                    .add("time", time)
                    .add("heading", heading)
                    .add("destBarcode", destBarcode)
                    .add("user", user)
                    .add("externalName", externalName)
                    .add("bioStateDesc", bioStateDesc)
                    .reprStringValues()
                    .toString();
        }
    }

    public record Link(int src, int dest) {}

    public List<Node> getNodes() {
        return this.nodes;
    }

    public List<Link> getLinks() {
        return this.links;
    }
}
