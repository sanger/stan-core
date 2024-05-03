package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a section registration request from an Excel file.
 */
public interface SectionRegisterFileReader extends MultipartFileReader<SectionRegisterRequest> {
    /** The relevant sheet in the excel file to read. */
    int SHEET_INDEX = 3;
    /** Column headings expected in the Excel file. */
    enum Column implements IColumn {
        Work_number(Pattern.compile("(work|sgp)\\s*number", Pattern.CASE_INSENSITIVE)),
        Slide_type,
        External_slide_ID,
        Prebarcode(String.class, Pattern.compile("(xenium( slide)?\\s*|pre)barcode", Pattern.CASE_INSENSITIVE), false),
        Section_address,
        Fixative,
        Embedding_medium,
        Donor_ID,
        Life_stage,
        Species,
        HuMFre(Pattern.compile("humfre\\s*(number)?", Pattern.CASE_INSENSITIVE)),
        Tissue_type,
        Spatial_location(Integer.class, Pattern.compile("spatial\\s*location\\s*(number)?", Pattern.CASE_INSENSITIVE), true),
        Replicate_number,
        Section_external_ID,
        Section_number(Integer.class),
        Section_thickness(Integer.class),
        Section_position(Pattern.compile("(if.+)?(section\\s+)?position", Pattern.CASE_INSENSITIVE)),
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

        /** The data type (String or Integer) expected in the column. */
        @Override
        public Class<?> getDataType() {
            return this.dataType;
        }

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
    default SectionRegisterRequest read(MultipartFile multipartFile) throws IOException, ValidationException {
        try (Workbook wb = WorkbookFactory.create(multipartFile.getInputStream())) {
            if (SHEET_INDEX < 0 || SHEET_INDEX >= wb.getNumberOfSheets()) {
                throw new ValidationException(List.of("Workbook does not have a worksheet at index "+SHEET_INDEX));
            }
            return read(wb.getSheetAt(SHEET_INDEX));
        }
    }

    /**
     * Reads the registration request in the given Excel sheet.
     * @param sheet the worksheet
     * @return a request read from the sheet
     * @exception ValidationException the request is invalid
     */
    SectionRegisterRequest read(Sheet sheet) throws ValidationException;

}
