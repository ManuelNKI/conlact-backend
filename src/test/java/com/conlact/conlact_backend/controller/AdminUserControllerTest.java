package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.user.CreateUserRequest;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.dto.user.UpdateUserRequest;
import com.conlact.conlact_backend.dto.user.UserListResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.service.UserManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.conlact.conlact_backend.security.UserPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private AdminUserController adminUserController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new UserPrincipal(UUID.randomUUID(), "admin@conlact.ec", "Admin User", "admin");
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("Admin Usuarios: GET /api/admin/usuarios retorna lista con formato {users: [...]}")
    void testListUsers_SpanishRoute() throws Exception {
        StaffUserResponse user = StaffUserResponse.builder()
                .id(UUID.randomUUID())
                .fullName("David Barragán")
                .email("david@conlact.ec")
                .role("admin")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userManagementService.listUsers()).thenReturn(new UserListResponse(List.of(user)));

        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users[0].full_name").value("David Barragán"))
                .andExpect(jsonPath("$.users[0].email").value("david@conlact.ec"))
                .andExpect(jsonPath("$.users[0].role").value("admin"))
                .andExpect(jsonPath("$.users[0].is_active").value(true));
    }

    @Test
    @DisplayName("Admin Usuarios: GET /api/admin/users alias en inglés funciona")
    void testListUsers_EnglishRoute() throws Exception {
        when(userManagementService.listUsers()).thenReturn(new UserListResponse(List.of()));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    @DisplayName("Admin Usuarios: POST /api/admin/usuarios crea usuario y retorna 201 {success: true, user: ...}")
    void testCreateUser_Success() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Elena Proaño",
                "elena@conlact.ec",
                "editor",
                "Contrasena1!",
                "Contrasena1!"
        );

        StaffUserResponse created = StaffUserResponse.builder()
                .id(UUID.randomUUID())
                .fullName("Elena Proaño")
                .email("elena@conlact.ec")
                .role("editor")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userManagementService.createUser(any(CreateUserRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/admin/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.full_name").value("Elena Proaño"))
                .andExpect(jsonPath("$.user.role").value("editor"));
    }

    @Test
    @DisplayName("Admin Usuarios: PATCH /api/admin/usuarios/{id} actualiza usuario y retorna 200 {success: true, user: ...}")
    void testUpdateUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = new UpdateUserRequest("Elena Editada", "admin", true);

        StaffUserResponse updated = StaffUserResponse.builder()
                .id(userId)
                .fullName("Elena Editada")
                .email("elena@conlact.ec")
                .role("admin")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userManagementService.updateUser(eq(userId), any(UpdateUserRequest.class), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/usuarios/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.full_name").value("Elena Editada"))
                .andExpect(jsonPath("$.user.role").value("admin"));
    }

    @Test
    @DisplayName("Admin Usuarios: DELETE /api/admin/usuarios/{id} retorna 200 {success: true}")
    void testDeleteUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(userManagementService).deleteUser(eq(userId), any());

        mockMvc.perform(delete("/api/admin/usuarios/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
