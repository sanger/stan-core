package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Reads a block registration request from an Excel file.
 */
public interface BlockRegisterFileReader extends MultipartFileReader<RegisterRequest> {
    /** Column headings expected in the Excel file. */
    enum Column implements IColumn {
        _preamble(Void.class, Pattern.compile("all\\s*information.*needed", Pattern.CASE_INSENSITIVE)),
        Work_number(Pattern.compile("(work|sgp)\\s*number", Pattern.CASE_INSENSITIVE)),
        Donor_identifier(Pattern.compile("donor\\s*id.*", Pattern.CASE_INSENSITIVE)),
        Life_stage,
        Collection_date(LocalDate.class, Pattern.compile("(if.*)date.*collection.*", Pattern.CASE_INSENSITIVE)),
        Species,
        HuMFre,
        Tissue_type,
        External_identifier(Pattern.compile("external\\s*id.*", Pattern.CASE_INSENSITIVE)),
        Spatial_location(Integer.class),
        Replicate_number,
        Last_known_section(Integer.class, Pattern.compile("last.*section.*", Pattern.CASE_INSENSITIVE)),
        Labware_type,
        Fixative,
        Embedding_medium(Pattern.compile("(embedding)?\\s*medium", Pattern.CASE_INSENSITIVE)),
        Comment(Void.class, Pattern.compile("(information|comment).*", Pattern.CASE_INSENSITIVE)),
        ;

        private final Pattern pattern;
        private final Class<?> dataType;

        Column() {
            this(null, null);
        }

        Column(Pattern pattern) {
            this(null, pattern);
        }

        Column(Class<?> dataType) {
            this(dataType, null);
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
            return read(wb.getSheetAt(2));
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
