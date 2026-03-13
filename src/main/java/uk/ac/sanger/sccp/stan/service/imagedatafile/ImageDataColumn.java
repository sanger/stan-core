package uk.ac.sanger.sccp.stan.service.imagedatafile;

import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.util.function.Function;

/** Columns in the image data file */
public enum ImageDataColumn implements TsvColumn<ImageDataRow> {
    location(null),
    filename(ImageDataRow::getBarcode),
    omero_name(ImageDataRow::getExternalName),
    omero_group(ImageDataRow::getOmeroProject),
    omero_project(ImageDataRow::getWorkNumber),
    omero_dataset(ImageDataRow::getExternalName),
    omero_username(ImageDataRow::getUserName),
    tags(null),
    comments(ImageDataRow::getComments),
    ;

    private final Function<ImageDataRow, String> dataGetter;

    ImageDataColumn(Function<ImageDataRow, String> dataGetter) {
        this.dataGetter = dataGetter;
    }

    @Override
    public String get(ImageDataRow entry) {
        return (dataGetter==null ? null : dataGetter.apply(entry));
    }
}
