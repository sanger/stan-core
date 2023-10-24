package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Excel reader to parse original samples registration request
 * @author dr6
 */
public interface OriginalSampleRegisterFileReader extends MultipartFileReader<OriginalSampleRegisterRequest> {
    /** Which sheet to read from the excel file? */
    int SHEET_INDEX = 1;

    /** Columns expected in the excel file */
    enum Column implements IColumn {
        _preamble(Void.class, Pattern.compile("mand.tory.*", Pattern.CASE_INSENSITIVE), false),
        Work_number(Pattern.compile("(work|sgp)\\s*number", Pattern.CASE_INSENSITIVE), false),
        Donor_identifier(Pattern.compile("donor\\s*id.*", Pattern.CASE_INSENSITIVE)),
        Life_stage,
        Collection_date(LocalDate.class, Pattern.compile("(if.*)date.*collection.*", Pattern.CASE_INSENSITIVE), false),
        Species,
        HuMFre(null, false),
        Tissue_type,
        External_identifier(Pattern.compile("external\\s*id.*", Pattern.CASE_INSENSITIVE), false),
        Spatial_location(Integer.class),
        Replicate_number(Pattern.compile("replicate.*", Pattern.CASE_INSENSITIVE), false),
        Labware_type,
        Fixative,
        Solution(Pattern.compile("solution.*", Pattern.CASE_INSENSITIVE)),
        _postramble(Void.class, Pattern.compile("info.*", Pattern.CASE_INSENSITIVE), false),
        ;

        private final Pattern pattern;
        private final Class<?> dataType;
        private final boolean required;

        Column() {
            this(null, null, true);
        }

        Column(Pattern pattern) {
            this(null, pattern, true);
        }

        Column(Pattern pattern, boolean required) {
            this(null, pattern, required);
        }

        Column(Class<?> dataType) {
            this(dataType, null, true);
        }

        Column(Class<?> dataType, Pattern pattern, boolean required) {
            this.pattern = (pattern!=null ? pattern : Pattern.compile(this.name().replace("_", "\\s*"), Pattern.CASE_INSENSITIVE));
            this.dataType = (dataType!=null ? dataType : String.class);
            this.required = required;
        }

        @Override
        public String toString() {
            return this.name().replace('_',' ');
        }

        /** The data type expected in the column. */
        @Override
        public Class<?> getDataType() {
            return this.dataType;
        }

        /** The regex pattern used to match this column's heading. */
        @Override
        public Pattern getPattern() {
            return this.pattern;
        }

        @Override
        public boolean isRequired() {
            return this.required;
        }
    }


    /**
     * Reads the given MultipartFile as an Excel file.
     * @param multipartFile an uploaded file
     * @return a request read from the file data
     * @exception IOException the file cannot be read
     * @exception ValidationException the request is invalid
     * */
    @Override
    default OriginalSampleRegisterRequest read(MultipartFile multipartFile) throws IOException, ValidationException {
        try (Workbook wb = WorkbookFactory.create(multipartFile.getInputStream())) {
            return read(wb.getSheetAt(SHEET_INDEX));
        }
    }

    /**
     * Reads the registration request in the given Excel sheet.
     * @param sheet the worksheet
     * @return a request read from the sheet
     * @exception ValidationException the request is invalid
     */
    OriginalSampleRegisterRequest read(Sheet sheet) throws ValidationException;
}
