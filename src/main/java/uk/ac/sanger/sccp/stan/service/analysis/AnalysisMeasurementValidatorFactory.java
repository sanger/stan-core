package uk.ac.sanger.sccp.stan.service.analysis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.service.analysis.AnalysisMeasurementValidator.AnalysisType;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

import java.util.*;

/**
 * Factory for {@link AnalysisMeasurementValidator}
 * @author dr6
 */
@Service
public class AnalysisMeasurementValidatorFactory {
    private final Map<AnalysisType, List<String>> allowedMeasurementNames;
    private final Map<AnalysisType, Sanitiser<String>> sanitisers;

    @Autowired
    public AnalysisMeasurementValidatorFactory(@Qualifier("rinSanitiser") Sanitiser<String> rinSanitiser,
                                               @Qualifier("dv200Sanitiser") Sanitiser<String> dv200Sanitiser) {
        allowedMeasurementNames = new EnumMap<>(AnalysisType.class);
        allowedMeasurementNames.put(AnalysisType.RIN, List.of(AnalysisMeasurementValidator.RIN_VALUE_NAME));
        allowedMeasurementNames.put(AnalysisType.DV200, List.of(AnalysisMeasurementValidator.DV200_VALUE_NAME,
                AnalysisMeasurementValidator.DV200_LOWER_NAME, AnalysisMeasurementValidator.DV200_UPPER_NAME));

        sanitisers = new EnumMap<>(AnalysisType.class);
        sanitisers.put(AnalysisType.RIN, rinSanitiser);
        sanitisers.put(AnalysisType.DV200, dv200Sanitiser);
    }

    public MeasurementValidator makeValidator(AnalysisType analysisType) {
        return new AnalysisMeasurementValidator(analysisType, sanitisers.get(analysisType),
                allowedMeasurementNames.get(analysisType));
    }
}
