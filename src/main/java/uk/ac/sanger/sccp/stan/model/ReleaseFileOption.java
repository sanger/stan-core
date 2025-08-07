package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Options for customising the content of the release file
 * @author dr6
 */
public enum ReleaseFileOption {
    Sample_processing("Sample processing"),
    Histology,
    RNAscope_IHC("RNAscope/IHC", true),
    Visium,
    Xenium,
    ;

    private static final UCMap<ReleaseFileOption> NAME_OPTIONS = UCMap.from(ReleaseFileOption::getQueryParamName, values());

    private final String displayName;
    private final boolean enabled;

    ReleaseFileOption(String displayName, boolean enabled) {
        this.displayName = (displayName != null ? displayName : this.name());
        this.enabled = enabled;
    }

    ReleaseFileOption(String displayName) {
        this(displayName, true);
    }

    ReleaseFileOption() {
        this(null);
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getQueryParamName() {
        return this.name();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ReleaseFileOption forParameterName(String name) {
        ReleaseFileOption option = NAME_OPTIONS.get(name);
        if (option==null) {
            throw new IllegalArgumentException("Unknown release file option: "+repr(name));
        }
        return option;
    }

    public static Optional<ReleaseFileOption> optForParameterName(String name) {
        return Optional.ofNullable(NAME_OPTIONS.get(name));
    }

}
