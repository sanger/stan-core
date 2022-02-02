package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.GraphQLCustomTypes;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.util.function.Function;

public enum WorkProgressColumn implements TsvColumn<WorkProgress> {
    Work_number(Work::getWorkNumber),
    Status(Work::getStatus),
    Work_type(Work::getWorkType),
    Last_section("section"),
    Last_stain("stain"),
    Last_RNAscope_or_IHC_stain("RNAscope/IHC stain"),
    Last_image("image"),
    Last_RNA_extract("extract"),
    Last_RNA_analysis("analysis"),
    Last_stain_Visium_TO("stain visium to"),
    Last_stain_Visium_LP("stain visium lp"),
    Last_CDNA_transfer("visium cdna"),
    ;

    private final Function<Work, ?> workFunction;
    private final String event;

    WorkProgressColumn(Function<Work, ?> workFunction) {
        this.workFunction = workFunction;
        this.event = null;
    }

    WorkProgressColumn(String event) {
        this.workFunction = null;
        this.event = event;
    }

    @Override
    public String get(WorkProgress entry) {
        if (this.workFunction != null) {
            Object value = this.workFunction.apply(entry.getWork());
            return (value==null ? "" : value.toString());
        }
        if (this.event != null) {
            return entry.getTimestamps().stream()
                    .filter(ts -> event.equalsIgnoreCase(ts.getType()))
                    .findAny()
                    .map(ts -> GraphQLCustomTypes.DATE_TIME_FORMAT.format(ts.getTimestamp()))
                    .orElse("");
        }
        return "";
    }

    @Override
    public String toString() {
        return this.name().replace('_',' ');
    }
}
