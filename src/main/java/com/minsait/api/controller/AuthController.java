package com.minsait.api.controller;

import com.minsait.api.controller.dto.GetTokenRequest;
import com.minsait.api.controller.dto.GetTokenResponse;
import com.minsait.api.repository.UsuarioEntity;
import com.minsait.api.repository.UsuarioRepository;
import com.minsait.api.sicurity.util.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping(value = "/auth")
public class AuthController {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JWTUtil jwtUtil;

    @PostMapping("/get-token")
    public ResponseEntity<GetTokenResponse> getToken(@RequestBody GetTokenRequest request) {
        if(request.getUserName().isEmpty() || request.getPassword().isEmpty()){
            return new ResponseEntity<>(GetTokenResponse.builder().build(), HttpStatus.BAD_REQUEST);
        }

        final UsuarioEntity usuario = this.usuarioRepository.findByLogin(request.getUserName());

        if(usuario != null && usuario.isValidPassword(request.getPassword())) {
            final ArrayList<String> permissoes = new ArrayList<>(usuario.listPermissoes());

            final var token = jwtUtil.generateToken(
                    usuario.getLogin(),
                    permissoes,
                    usuario.getId().intValue()
            );

            return new ResponseEntity<>(
                    GetTokenResponse.builder().accessToken(token).build(),
                    HttpStatus.OK
            );
        } else {
            return new ResponseEntity<>(GetTokenResponse.builder().build(), HttpStatus.UNAUTHORIZED);
        }

    }
}
