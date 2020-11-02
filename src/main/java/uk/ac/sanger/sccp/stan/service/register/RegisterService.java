package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;

public interface RegisterService {
    void register(RegisterRequest request, User user);
}
