package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterResult;

public interface RegisterService {
    RegisterResult register(RegisterRequest request, User user);
}
