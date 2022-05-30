package uk.ac.sanger.sccp.stan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.sanger.sccp.stan.service.StringValidator;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.sanitiser.*;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Holder for the validators for certain fields
 * @author dr6
 */
@Configuration
public class FieldValidation {
    @Bean
    public Validator<String> donorNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT,
                CharacterType.HYPHEN, CharacterType.UNDERSCORE, CharacterType.SPACE,
                CharacterType.FULL_STOP, CharacterType.SLASH, CharacterType.BACKSLASH,
                CharacterType.COMMA, CharacterType.COLON, CharacterType.SEMICOLON
        );
        return new StringValidator("Donor identifier", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> visiumLPBarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN);
        Pattern pattern = Pattern.compile("[0-9]{7}[0-9A-Z]+-[0-9]+-[0-9]+-[0-9]+", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Visium LP barcode", 14, 32, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> tubePrebarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        Pattern pattern = Pattern.compile("[A-Z]{2}[0-9]{8}", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Prebarcode", 10, 10, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> externalNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN,
                CharacterType.UNDERSCORE
        ) ;
        return new StringValidator("External identifier", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> externalBarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN,
                CharacterType.UNDERSCORE
        ) ;
        return new StringValidator("External barcode", 3, 32, charTypes);
    }

    @Bean
    public Validator<String> replicateValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        Pattern pattern = Pattern.compile("[1-9][0-9]*[a-z]?", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Replicate number", 1, 8, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> commentCategoryValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.SPACE);
        return new StringValidator("Comment category", 2, 32, charTypes);
    }

    @Bean
    public Validator<String> commentTextValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE, CharacterType.COLON,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Comment text", 3, 128, charTypes);
    }

    @Bean
    public Validator<String> equipmentCategoryValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA);
        return new StringValidator("Equipment category", 2, 32, charTypes);
    }

    @Bean
    public Validator<String> equipmentNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Equipment name", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> usernameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        return new StringValidator("Username", 1, 32, charTypes);
    }

    @Bean
    public Validator<String> hmdmcValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.DIGIT, CharacterType.SLASH);
        Pattern pattern = Pattern.compile("\\d{2}/\\d{2,}");
        return new StringValidator("HuMFre", 1, 16, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> workPriorityValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        Pattern pattern = Pattern.compile("[A-Z][0-9]+", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Priority", 2, 8, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> destructionReasonValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Destruction reason", 3, 128, charTypes);
    }

    @Bean
    public Validator<String> releaseDestinationValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.APOSTROPHE
        );
        return new StringValidator("Release destination", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> releaseRecipientValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        return new StringValidator("Release recipient", 1, 16, charTypes);
    }

    @Bean
    public Validator<String> speciesValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.APOSTROPHE
        );
        return new StringValidator("Species", 1, 64, charTypes);
    }

    @Bean
    public Validator<String> projectNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Project name", 2, 64, charTypes);
    }

    @Bean
    public Validator<String> solutionSampleValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Solution sample", 2, 64, charTypes);
    }

    @Bean
    public Validator<String> fixativeNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE,
                CharacterType.PERCENT
        );
        return new StringValidator("Fixative name", 2, 64, charTypes);
    }

    @Bean
    public Validator<String> workTypeNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN, CharacterType.SPACE,
                CharacterType.SLASH, CharacterType.PAREN, CharacterType.FULL_STOP, CharacterType.APOSTROPHE
        );
        return new StringValidator("Work type name", 2, 64, charTypes);
    }

    @Bean
    public Validator<String> costCodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT);
        Pattern pattern = Pattern.compile("[A-Z][0-9]+", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Cost code", 2, 10, charTypes, false, pattern);
    }

    @Bean
    public Sanitiser<String> concentrationSanitiser() {
        return new DecimalSanitiser("concentration", 2, null, null);
    }

    @Bean
    public Sanitiser<String> rinSanitiser() {
        return new DecimalSanitiser("RIN", 1, null, null);
    }

    @Bean
    public Sanitiser<String> dv200Sanitiser() {
        return new DecimalSanitiser("DV200", 1, BigDecimal.ZERO, new BigDecimal(100));
    }

    @Bean
    public Sanitiser<String> cqSanitiser() {
        return new IntSanitiser("Cq value", null, null);
    }

    @Bean
    public Sanitiser<String> tissueCoverageSanitiser() {
        return new IntSanitiser("Tissue coverage", 0, 100);
    }

    @Bean
    public Validator<String> reagentPlateBarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.DIGIT);
        return new StringValidator("Reagent plate barcode", 24, 24, charTypes);
    }
}
