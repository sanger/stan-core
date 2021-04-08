package uk.ac.sanger.sccp.stan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.sanger.sccp.stan.service.StringValidator;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;
import uk.ac.sanger.sccp.stan.service.Validator;

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
                CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT,
                CharacterType.HYPHEN, CharacterType.UNDERSCORE, CharacterType.SPACE
        );
        return new StringValidator("Donor identifier", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> visiumLPBarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT, CharacterType.HYPHEN);
        Pattern pattern = Pattern.compile("[0-9]{7}[0-9A-Z]+-[0-9]+-[0-9]+-[0-9]+", Pattern.CASE_INSENSITIVE);
        return new StringValidator("Visium LP barcode", 14, 32, charTypes, false, pattern);
    }

    @Bean
    public Validator<String> externalNameValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT, CharacterType.HYPHEN,
                CharacterType.UNDERSCORE
        ) ;
        return new StringValidator("External identifier", 3, 64, charTypes);
    }

    @Bean
    public Validator<String> externalBarcodeValidator() {
        Set<CharacterType> charTypes = EnumSet.of(
                CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT, CharacterType.HYPHEN,
                CharacterType.UNDERSCORE
        ) ;
        return new StringValidator("External barcode", 3, 32, charTypes);
    }
}
