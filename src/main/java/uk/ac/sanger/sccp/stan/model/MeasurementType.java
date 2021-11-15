package uk.ac.sanger.sccp.stan.model;

/**
 * @author dr6
 */
public enum MeasurementType {
    Thickness(MeasurementValueType.INT),
    Haematoxylin(MeasurementValueType.TIME),
    Eosin(MeasurementValueType.TIME),
    Blueing(MeasurementValueType.TIME),
    Concentration(MeasurementValueType.DECIMAL_2),
    Permabilisation_time(MeasurementValueType.TIME),
    Selected_time(MeasurementValueType.TIME),
    ;

    private final MeasurementValueType valueType;

    MeasurementType(MeasurementValueType valueType) {
        this.valueType = valueType;
    }

    public MeasurementValueType getValueType() {
        return this.valueType;
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
