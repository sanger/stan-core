package uk.ac.sanger.sccp.stan;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author dr6
 */
@Component
public class AuthenticationComponent {
    public void setAuthentication(Authentication authentication, int maxInactiveMinutes) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (authentication!=null) {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            attr.getRequest().getSession().setMaxInactiveInterval(60 * maxInactiveMinutes);
        }
    }

    public Authentication getAuthentication() {
        SecurityContext sc = SecurityContextHolder.getContext();
        return (sc==null ? null : sc.getAuthentication());
    }
}
