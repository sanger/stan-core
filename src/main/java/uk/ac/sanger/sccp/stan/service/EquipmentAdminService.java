package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Equipment;
import uk.ac.sanger.sccp.stan.repo.EquipmentRepo;

import javax.persistence.EntityExistsException;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;
import static uk.ac.sanger.sccp.utils.BasicUtils.trimAndRequire;

/**
 * Service for dealing with {@link Equipment}
 * @author dr6
 */
@Service
public class EquipmentAdminService {
    private final EquipmentRepo equipmentRepo;
    private final Validator<String> equipmentCategoryValidator;
    private final Validator<String> equipmentNameValidator;

    public EquipmentAdminService(EquipmentRepo equipmentRepo,
                                 @Qualifier("equipmentCategoryValidator") Validator<String> equipmentCategoryValidator,
                                 @Qualifier("equipmentNameValidator") Validator<String> equipmentNameValidator) {
        this.equipmentRepo = equipmentRepo;
        this.equipmentCategoryValidator = equipmentCategoryValidator;
        this.equipmentNameValidator = equipmentNameValidator;
    }

    public Iterable<Equipment> getEquipment(String category, boolean includeDisabled) {
        if (category==null) {
            return (includeDisabled ? equipmentRepo.findAll() : equipmentRepo.findAllByEnabled(true));
        }
        return (includeDisabled ? equipmentRepo.findAllByCategory(category) : equipmentRepo.findAllByCategoryAndEnabled(category, true));
    }

    public Equipment addEquipment(String category, String name) {
        category = trimAndRequire(category, "Category not supplied.");
        name = trimAndRequire(name, "Name not supplied.");
        equipmentCategoryValidator.checkArgument(category);
        equipmentNameValidator.checkArgument(name);
        var optEquipment = equipmentRepo.findByCategoryAndName(category, name);
        if (optEquipment.isPresent()) {
            throw new EntityExistsException(String.format("Equipment already exists: (category=%s, name=%s)",
                    repr(category), repr(name)));
        }
        return equipmentRepo.save(new Equipment(name, category.toLowerCase()));
    }

    public Equipment renameEquipment(int equipmentId, String newName) {
        newName = trimAndRequire(newName, "Name not supplied.");
        equipmentNameValidator.checkArgument(newName);
        Equipment eq = equipmentRepo.getById(equipmentId);
        if (newName.equals(eq.getName())) {
            return eq;
        }
        eq.setName(newName);
        return equipmentRepo.save(eq);
    }

    public Equipment setEquipmentEnabled(int equipmentId, boolean enabled) {
        Equipment eq = equipmentRepo.getById(equipmentId);
        if (eq.isEnabled() != enabled) {
            eq.setEnabled(enabled);
            eq = equipmentRepo.save(eq);
        }
        return eq;
    }
}
