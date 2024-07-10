package uk.ac.sanger.sccp.stan.model;

/**
 * Types of measurements, used to alter how different types of measurements are displayed
 * @author dr6
 */
public enum MeasurementType {
    Thickness(MeasurementValueType.INT, "μm"),
    Haematoxylin(MeasurementValueType.TIME),
    Eosin(MeasurementValueType.TIME),
    Blueing(MeasurementValueType.TIME),
    cDNA_concentration(MeasurementValueType.DECIMAL, "pg/μL"),
    Library_concentration(MeasurementValueType.DECIMAL, "pg/μL"),
    RNA_concentration(MeasurementValueType.DECIMAL, "ng/μL"),
    Permeabilisation_time(MeasurementValueType.TIME),
    Selected_time(MeasurementValueType.TIME),
    DV200(MeasurementValueType.DECIMAL, "%"),
    Tissue_coverage(MeasurementValueType.INT, "%"),
    Cq_value(MeasurementValueType.DECIMAL),
    Cycles(MeasurementValueType.INT),
    Size_bp(MeasurementValueType.INT, "bp"),
    ;

    private final MeasurementValueType valueType;
    private final String unit;

    MeasurementType(MeasurementValueType valueType, String unit) {
        this.valueType = valueType;
        this.unit = unit;
    }

    MeasurementType(MeasurementValueType valueType) {
        this(valueType, null);
    }

    public MeasurementValueType getValueType() {
        return this.valueType;
    }

    public String getUnit() {
        return this.unit;
    }

    public String friendlyName() {
        return this.name().replace('_',' ');
    }

    public static MeasurementType forName(String name) {
        if (name!=null) {
            name = name.replace(' ','_');
            for (MeasurementType mt : values()) {
                if (mt.name().equalsIgnoreCase(name)) {
                    return mt;
                }
            }
        }
        return null;
    }
}
