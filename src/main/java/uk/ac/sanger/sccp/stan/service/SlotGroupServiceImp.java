package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SlotGroupRepo;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/**
 * @author dr6
 */
@Service
public class SlotGroupServiceImp implements SlotGroupService {
    private final SlotGroupRepo slotGroupRepo;

    @Autowired
    public SlotGroupServiceImp(SlotGroupRepo slotGroupRepo) {
        this.slotGroupRepo = slotGroupRepo;
    }

    @Override
    public List<SlotGroup> saveGroups(Labware lw, Integer planId, Collection<? extends Collection<Address>> groups) {
        int groupIndex = 1;
        List<SlotGroup> records = new ArrayList<>();
        for (Collection<Address> group : groups) {
            for (Address address : group) {
                Slot slot = lw.getSlot(address);
                records.add(new SlotGroup(groupIndex, slot, planId));
            }
            ++groupIndex;
        }
        if (records.isEmpty()) {
            return List.of();
        }
        return asList(slotGroupRepo.saveAll(records));
    }

    @Override
    public List<List<Address>> loadGroups(Integer planId) {
        List<SlotGroup> records = slotGroupRepo.findByPlanId(planId);
        if (records.isEmpty()) {
            return List.of();
        }
        records = records.stream()
                .sorted(Comparator.comparing(SlotGroup::getGroupIndex)
                        .thenComparing(g -> g.getSlot().getAddress()))
                .toList();
        int currentGroupIndex = -1;
        List<List<Address>> groups = new ArrayList<>();
        List<Address> currentGroup = null;
        for (SlotGroup record : records) {
            if (currentGroup == null || record.getGroupIndex() != currentGroupIndex) {
                currentGroup = new ArrayList<>();
                groups.add(currentGroup);
                currentGroupIndex = record.getGroupIndex();
            }
            currentGroup.add(record.getSlot().getAddress());
        }
        return groups;
    }
}
