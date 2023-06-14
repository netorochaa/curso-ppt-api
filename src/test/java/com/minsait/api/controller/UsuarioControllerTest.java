package com.minsait.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jayway.jsonpath.JsonPath;
import com.minsait.api.controller.dto.GetTokenRequest;
import com.minsait.api.controller.dto.UsuarioRequest;
import com.minsait.api.repository.UsuarioRepository;
import com.minsait.api.sicurity.util.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Teste endpoints Usuario")
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    JWTUtil jwtUtil;

    private String token;
    private ObjectWriter ow;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    public void init(){
        ow = MAPPER.writer().withDefaultPrettyPrinter();
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_USUARIO");
        authorities.add("ESCRITA_USUARIO");
        token = jwtUtil.generateToken("admin", authorities, 5);
    }

    @Test
    @DisplayName("Deve retornar todos os usuarios")
    void usuarioFindAll() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content.[0].id").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content.[0].login").value("root"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content.[0].senha").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content.[0].permissoes").isNotEmpty())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve inserir um usuário")
    void insert() throws Exception {
        final var request = new UsuarioRequest();
        request.setEmail("abdias@outlook.com");
        request.setNome("Abdias");
        request.setLogin("abdias");
        request.setSenha("12345");
        request.setEmail("abdias@outlook.com");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.post("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.nome").value(request.getNome()))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andDo(MockMvcResultHandlers.print());

        //verifica se o usuário foi inserido no banco
        final var usuarioInserido = usuarioRepository.findByLogin(request.getLogin());
        assertEquals(request.getLogin(), usuarioInserido.getLogin());

        //verifica se o token gerado contem as mesmas permissões do usuário inserido
        final var requestGetToken = new GetTokenRequest();
        requestGetToken.setUserName(request.getLogin());
        requestGetToken.setPassword(request.getSenha());
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.post("/auth/get-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(requestGetToken))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").isNotEmpty())
                .andDo(MockMvcResultHandlers.print());

        MvcResult result = resultActions.andReturn();
        String contentAsString = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(contentAsString, "$.accessToken");
        final var tokenClaims = jwtUtil.getClaims(accessToken);
        final ArrayList<String> authorities =  (ArrayList<String>) tokenClaims.get("authorities");

        //verifica se as perimssões do request batem com as permissões inseridas no token
        assertTrue(authorities.contains("ESCRITA_CLIENTE"));
        assertTrue(authorities.contains("LEITURA_CLIENTE"));
        assertTrue(authorities.contains("ESCRITA_USUARIO"));
        assertTrue(authorities.contains("LEITURA_USUARIO"));
    }

    @Test
    @DisplayName("Deve atualizar um usuário sem alterar a senha")
    void update() throws Exception {
        final var request = new UsuarioRequest();
        request.setId(1L);
        request.setEmail("test-update@test.com");
        request.setNome("Root update");
        request.setLogin("root");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.put("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.nome").value("Root update"))
                .andDo(MockMvcResultHandlers.print());

        //verifica se o usuário foi alterado no banco
        final var usuarioAlterado = usuarioRepository.findByLogin(request.getLogin());
        assertEquals(request.getNome(), usuarioAlterado.getNome());

        //verifica se a senha do usuário não foi alterada ou descriptogravada
        //Ao alterar um usuarío, não é obrigatório alterar a senha. Se a senha não for enviada no request,
        //deve manter a senha antiga ciptografada que já está salva no banco.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("12345", usuarioAlterado.getSenha()));
    }

    @Test
    @DisplayName("Deve atualizar um usuário alterando a senha")
    void updatePasword() throws Exception {
        final var request = new UsuarioRequest();
        request.setId(1L);
        request.setEmail("test-update@test.com");
        request.setNome("Root update");
        request.setSenha("nova-senha");
        request.setLogin("root");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.put("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.nome").value("Root update"))
                .andDo(MockMvcResultHandlers.print());

        //verifica se o usuário foi alterado no banco
        final var usuarioAlterado = usuarioRepository.findByLogin(request.getLogin());
        assertEquals(request.getNome(), usuarioAlterado.getNome());

        //verifica se a senha foi alerada no banco
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches(request.getSenha(), usuarioAlterado.getSenha()));

        //reseta a senha do usuarío root para não quebrar outros testes
        usuarioAlterado.setLogin("root");
        usuarioAlterado.setSenha(new BCryptPasswordEncoder().encode("12345"));
        usuarioRepository.save(usuarioAlterado);
    }

    @Test
    @DisplayName("Deve retornar 404 quando tentar atualizar usuario não encontrado")
    void updateNotFound() throws Exception {
        final var request = new UsuarioRequest();
        request.setId(10L);
        request.setEmail("test-update@test.com");
        request.setNome("Root update");
        request.setLogin("root");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.put("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve excluir um usuario")
    void delete() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/usuario/2")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());

        //verifica se o usuário foi excluído do banco
        final var usuarioExcluido = usuarioRepository.findById(2L);
        assertTrue(usuarioExcluido.isEmpty());
    }

    @Test
    @DisplayName("Deve retornar 404 quando tentar excluir usuario não encontrado")
    void deleteNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/usuario/10")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve encontrar um usuario pelo id")
    void findById() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/usuario/1")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.senha").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.permissoes").isNotEmpty())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve retornar negar acesso quando tentar listar usuário sem permissão de leitura")
    public void forbbidenFindAll() throws Exception {
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_CLIENTE");
        authorities.add("ESCRITA_CLIENTE");
        token = jwtUtil.generateToken("admin", authorities, 5);

        mvc.perform(MockMvcRequestBuilders.get("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve retornar negar acesso quando tentar recuperar usuário sem permissão de leitura")
    public void forbbidenFindById() throws Exception {
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_CLIENTE");
        authorities.add("ESCRITA_CLIENTE");
        token = jwtUtil.generateToken("admin", authorities, 5);

        mvc.perform(MockMvcRequestBuilders.get("/api/usuario/1")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve retornar negar acesso quando tentar inserir usuário sem permissão de escrita")
    public void forbbidenInsert() throws Exception {
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_CLIENTE");
        authorities.add("ESCRITA_CLIENTE");
        token = jwtUtil.generateToken("admin", authorities, 5);

        final var request = new UsuarioRequest();
        request.setEmail("abdias@outlook.com");
        request.setNome("Abdias");
        request.setLogin("abdias");
        request.setSenha("12345");
        request.setEmail("abdias@outlook.com");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.post("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve retornar negar acesso quando tentar alterar usuário sem permissão de escrita")
    public void forbbidenUpdate() throws Exception {
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_CLIENTE");
        authorities.add("ESCRITA_CLIENTE");
        token = jwtUtil.generateToken("admin", authorities, 5);

        final var request = new UsuarioRequest();
        request.setId(1L);
        request.setEmail("test-update@test.com");
        request.setNome("Root update");
        request.setLogin("root");
        request.setPermissoes("ESCRITA_CLIENTE,LEITURA_CLIENTE,ESCRITA_USUARIO,LEITURA_USUARIO");
        mvc.perform(MockMvcRequestBuilders.put("/api/usuario")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ow.writeValueAsString(request))
                )
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("Deve retornar negar acesso quando tentar excluir usuário sem permissão de escrita")
    public void forbbidenDelete() throws Exception {
        ArrayList<String> authorities = new ArrayList<>();
        authorities.add("LEITURA_CLIENTE");
        authorities.add("ESCRITA_CLIENTE");
        token = jwtUtil.generateToken("admin", authorities, 5);

        mvc.perform(MockMvcRequestBuilders.delete("/api/usuario/2")
                        .header("Authorization", "Bearer ".concat(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andDo(MockMvcResultHandlers.print());
    }
}