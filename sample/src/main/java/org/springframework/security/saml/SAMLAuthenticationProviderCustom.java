package org.springframework.security.saml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.*;
import java.util.*;

public class SAMLAuthenticationProviderCustom extends SAMLAuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthenticationProviderCustom.class);
	@Override
public Collection<? extends GrantedAuthority> getEntitlements(SAMLCredential credential, Object userDetail)
    {

        logger.info("****** object is instance of UserDetails :"+ (userDetail instanceof UserDetails));

        if (userDetail instanceof UserDetails)
        {
            List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
            authorities.addAll(((UserDetails) userDetail).getAuthorities());
            return authorities;
        }
        else if(userDetail instanceof UsernamePasswordAuthenticationToken)
        {
             List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
             authorities.addAll(((UsernamePasswordAuthenticationToken) userDetail).getAuthorities());
             return authorities;

        } else {
            return Collections.emptyList();
        }
    }
}
