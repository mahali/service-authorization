/*
 * Copyright 2016 EPAM Systems
 * 
 * 
 * This file is part of EPAM Report Portal.
 * https://github.com/epam/ReportPortal
 * 
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */ 
/*
 * This file is part of Report Portal.
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.auth;

import com.epam.reportportal.auth.store.OAuth2AccessTokenRepository;
import com.epam.ta.reportportal.commons.Preconditions;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.util.SerializationUtils;
import org.springframework.security.oauth2.provider.*;
import org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.stereotype.Service;

import javax.inject.Provider;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Facade for {@link org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices} to simplify work with them
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@Service
public class TokenServicesFacade {

	private final OAuth2AccessTokenRepository tokenRepository;
	private final DefaultTokenServices tokenServices;
	private final OAuth2RequestFactory oAuth2RequestFactory;
	private final ClientDetailsService clientDetailsService;

	@Autowired
	public TokenServicesFacade(AuthorizationServerTokenServices tokenServices, OAuth2AccessTokenRepository tokenRepository,
			ClientDetailsService clientDetailsService) {
		this.tokenServices = (DefaultTokenServices) tokenServices;
		this.tokenRepository = tokenRepository;
		this.clientDetailsService = clientDetailsService;
		this.oAuth2RequestFactory = new DefaultOAuth2RequestFactory(clientDetailsService);
	}

	public OAuth2AccessToken getApiToken(String username) {
		List<OAuth2AccessToken> tokens = getApiTokens(username);
		BusinessRule.expect(tokens, Preconditions.NOT_EMPTY_COLLECTION).verify(ErrorType.USER_NOT_FOUND, username);
		return tokens.get(0);
	}

	public List<OAuth2AccessToken> getApiTokens(String username) {
		return tokenRepository
				.findByClientIdAndUserName(ReportPortalClient.api.name(), username)
				.stream().map(token -> SerializationUtils.<OAuth2AccessToken>deserialize(token.getToken()))
				.collect(Collectors.toList());
	}

	public void revokeToken(String token) {
		this.tokenServices.revokeToken(token);
	}

	public OAuth2AccessToken createToken(ReportPortalClient client, String username,
			Authentication userAuthentication) {
		//@formatter:off
		ClientDetails clientDetails = clientDetailsService.loadClientByClientId(client.name());
		OAuth2Request oAuth2Request = oAuth2RequestFactory.createOAuth2Request(clientDetails, oAuth2RequestFactory.createTokenRequest(
				ImmutableMap.<String, String>builder()
						.put("client_id", client.name())
						.put("username", username)
						.put("grant", "password")
						.build(), clientDetails));
		//@formatter:on
		return tokenServices.createAccessToken(new OAuth2Authentication(oAuth2Request, userAuthentication));
	}
}