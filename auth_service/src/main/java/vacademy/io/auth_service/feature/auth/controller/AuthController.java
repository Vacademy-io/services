package vacademy.io.auth_service.feature.auth.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vacademy.io.auth_service.feature.auth.dto.AuthRequestDto;
import vacademy.io.auth_service.feature.auth.dto.JwtResponseDto;
import vacademy.io.auth_service.feature.auth.dto.RegisterRequest;
import vacademy.io.auth_service.feature.auth.manager.AuthManager;
import vacademy.io.common.auth.dto.RefreshTokenRequestDTO;
import vacademy.io.common.auth.repository.UserRepository;
import vacademy.io.common.auth.service.JwtService;
import vacademy.io.common.auth.service.RefreshTokenService;


@RestController
@RequestMapping("/auth-service/v1")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    AuthManager authManager;


    @PostMapping("/signup-root")
    public JwtResponseDto registerUser(@RequestBody RegisterRequest registerRequest) {

        return authManager.registerRootUser(registerRequest);

    }

    @PostMapping("/login-root")
    public JwtResponseDto authenticateAndGetToken(@RequestBody AuthRequestDto authRequestDTO) {

        return authManager.loginUser(authRequestDTO);
    }


    @PostMapping("/refresh-token")
    public JwtResponseDto refreshToken(@RequestBody RefreshTokenRequestDTO refreshTokenRequestDTO) {
        return authManager.refreshToken(refreshTokenRequestDTO);
    }

}




