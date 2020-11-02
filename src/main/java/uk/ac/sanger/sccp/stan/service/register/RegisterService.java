package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;

import java.util.List;

public interface RegisterService {
    List<Labware> register(RegisterRequest request, User user);
}
