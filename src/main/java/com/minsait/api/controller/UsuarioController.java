package com.minsait.api.controller;

import com.minsait.api.controller.dto.UsuarioResponse;
import com.minsait.api.repository.UsuarioEntity;
import com.minsait.api.repository.UsuarioRepository;
import com.minsait.api.util.ObjectMapperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api")
public class UsuarioController {
    @Autowired
    private UsuarioRepository usuarioRepository;

    @PreAuthorize("hasAuthority('LEITURA_USUARIO')")
    @GetMapping("/usuario")
    public ResponseEntity<Page<UsuarioResponse>> findAll(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String email,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        final var usuarioEntity = new UsuarioEntity();
        usuarioEntity.setNome(nome);
        usuarioEntity.setLogin(login);
        usuarioEntity.setEmail(email);

        Pageable pageable = PageRequest.of(page, pageSize);

        final Page<UsuarioEntity> usuarioEntityListPage = usuarioRepository.findAll(
                usuarioEntity.usuarioEntitySpecification(),
                pageable
        );

        final Page<UsuarioResponse> usuarioResponseList = ObjectMapperUtil.mapAll(
                usuarioEntityListPage,
                UsuarioResponse.class
        );

        return ResponseEntity.ok(usuarioResponseList);
    }
}
