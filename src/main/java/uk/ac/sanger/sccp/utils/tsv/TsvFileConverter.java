package uk.ac.sanger.sccp.utils.tsv;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.*;
import org.springframework.http.converter.*;

import java.io.IOException;

/**
 * @author dr6
 */
public class TsvFileConverter<E> extends AbstractHttpMessageConverter<TsvFile<?>> {
    public static final MediaType MEDIA_TYPE = new MediaType("text", "tsv");

    public TsvFileConverter() {
        super(MEDIA_TYPE);
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
        output.getHeaders().setContentType(MEDIA_TYPE);
        output.getHeaders().set("Content-Disposition", "attachment; filename=\"" + rel.getFilename() + "\"");
        try (TsvWriter writer = new TsvWriter(output.getBody())) {
            writer.write(rel);
        }
    }
}
