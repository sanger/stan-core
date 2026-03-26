package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a block registration request from an Excel file.
 */
public interface BlockRegisterFileReader extends MultipartFileReader<RegisterRequest> {
    /** The relevant sheet in the excel file to read. */
    int SHEET_INDEX = 2;

    /** Column headings expected in the Excel file. */
    enum Column implements IColumn {
        _preamble(Void.class, Pattern.compile("mandatory.*|all\\s*information.*needed", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Work_number(Pattern.compile("(work|sgp)\\s*number.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Donor_identifier(Pattern.compile("donor\\s*id.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Life_stage,
        Collection_date(LocalDate.class, Pattern.compile("(if.*)?(date.*collection|collection.*date).*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Species,
        Cell_class(Pattern.compile("cell(ular)?\\s*class(ification)?", Pattern.CASE_INSENSITIVE)),
        Bio_risk(Pattern.compile("bio\\w*\\s+risk.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        HuMFre(Pattern.compile("humfre\\s*(number)?", Pattern.CASE_INSENSITIVE)),
        Tissue_type,
        External_identifier(Pattern.compile("external\\s*id.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Spatial_location(Integer.class, Pattern.compile("spatial\\s*location\\s*(number)?", Pattern.CASE_INSENSITIVE)),
        Replicate_number,
        Last_known_section(Integer.class, Pattern.compile("last.*section.*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        Labware_type,
        Fixative,
        Embedding_medium(Pattern.compile("(embedding)?\\s*medium", Pattern.CASE_INSENSITIVE)),
        Comment(Void.class, Pattern.compile("(information|comment).*", Pattern.CASE_INSENSITIVE|Pattern.DOTALL)),
        ;

        private final Pattern pattern;
        private final Class<?> dataType;

        Column() {
            this(null, null);
        }

        Column(Pattern pattern) {
            this(null, pattern);
        }

        Column(Class<?> dataType, Pattern pattern) {
            this.pattern = (pattern!=null ? pattern : Pattern.compile(this.name().replace("_", "\\s*"), Pattern.CASE_INSENSITIVE));
            this.dataType = (dataType!=null ? dataType : String.class);
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

        /** The regex pattern used to match this column's heading. */
        @Override
        public Pattern getPattern() {
            return this.pattern;
        }

        @Override
        public boolean isRequired() {
            return this.dataType != Void.class;
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
    default RegisterRequest read(MultipartFile multipartFile) throws IOException, ValidationException {
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
    RegisterRequest read(Sheet sheet) throws ValidationException;

}
