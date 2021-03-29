package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;

public interface RegisterService {
    RegisterResult register(RegisterRequest request, User user);
}
