package uk.ac.sanger.sccp.stan.service.imagedatafile;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * @author dr6
 */
public class ImageDataRow {
    private String barcode;
    private String externalName;
    private String omeroProject;
    private String workNumber;
    private String userName;
    private String comments;

    public ImageDataRow(String barcode, String externalName, String omeroProject, String workNumber, String userName,
                        String comments) {
        this.barcode = barcode;
        this.externalName = externalName;
        this.omeroProject = omeroProject;
        this.workNumber = workNumber;
        this.userName = userName;
        this.comments = comments;
    }

    public String getBarcode() {
        return this.barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getExternalName() {
        return this.externalName;
    }

    public void setExternalName(String externalName) {
        this.externalName = externalName;
    }

    public String getOmeroProject() {
        return this.omeroProject;
    }

    public void setOmeroProject(String omeroProject) {
        this.omeroProject = omeroProject;
    }

    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getComments() {
        return this.comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ImageDataRow that = (ImageDataRow) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.externalName, that.externalName)
                && Objects.equals(this.omeroProject, that.omeroProject)
                && Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.userName, that.userName)
                && Objects.equals(this.comments, that.comments));
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcode, externalName, omeroProject, workNumber, userName, comments);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("barcode", barcode)
                .add("externalName", externalName)
                .add("omeroProject", omeroProject)
                .add("workNumber", workNumber)
                .add("userName", userName)
                .add("comments", comments)
                .reprStringValues()
                .toString();
    }
}
