package uk.ac.sanger.sccp.utils.tsv;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author dr6
 */
public class TsvFileConverter extends AbstractHttpMessageConverter<TsvFile<?>> {
    public static final MediaType TSV_MEDIA_TYPE = new MediaType("text", "tsv"),
            XLSX_MEDIA_TYPE = new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public TsvFileConverter() {
        super(TSV_MEDIA_TYPE, XLSX_MEDIA_TYPE);
    }

    @Override
    protected boolean supports(@NotNull Class<?> cls) {
        return TsvFile.class.isAssignableFrom(cls);
    }

    @NotNull
    @Override
    protected TsvFile<?> readInternal(@NotNull Class<? extends TsvFile<?>> cls, @NotNull HttpInputMessage message)
            throws IOException, HttpMessageNotReadableException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void writeInternal(TsvFile<?> rel, HttpOutputMessage output) throws IOException, HttpMessageNotWritableException {
        boolean useTsv = BasicUtils.endsWithIgnoreCase(rel.getFilename(), "tsv");
        output.getHeaders().setContentType(useTsv ? TSV_MEDIA_TYPE : XLSX_MEDIA_TYPE);
        output.getHeaders().set("Content-Disposition", "attachment; filename=\"" + rel.getFilename() + "\"");
        OutputStream out = output.getBody();
        try (TableFileWriter writer = useTsv ? new TsvWriter(out) : new XlsxWriter(out)) {
            writer.write(rel);
        }
    }
}
